package com.lokad.flatfiles;

import it.unimi.dsi.fastutil.ints.IntArrayList;

import com.google.common.primitives.*;

import java.util.ArrayList;
import java.util.List;

/** 
 A trie data structure for matching cell contents with unique
 and sequential integer identifiers.
 
 
 This class is intended for maximum performance. The implementation,
 beyond being fine-tuned, follows a simple design constraint: 
  -> The system should perform (N + 2 log N) memory allocations,
	 where N is the number of different byte sequences stored.

 The trie uses compressed sequences. That is, to store sequences
 ABC and ABD, only three nodes are used: [AB], [C] and [D] ; this 
 is different from an uncompressed version where each node would hold 
 exactly one byte: [A], [B], [C] and [D]. 
 
 Each trie node contains the following fields:     
  -> 'Buffer' is a pointer to a buffer where the compressed sequence
	 is held.
  -> 'Start' and 'End' are the indices, within 'Buffer', where the 
	 compressed sequence is stored. This allows sharing buffers 
	 between nodes.
  -> 'First' is the first four bytes of the compressed sequence.
  -> 'Reference', if > 0, is the unique identifier of the prefix
	 ending at that node. 
  -> 'Children' is an array of children of the node, of size
	 <see cref="HashSizeAtLength"/> of the prefix length at that
	 node. The starting byte of each child, mod the size, is the
	 index of that child within the array. 
  -> 'NextSibling' is an implementation of a list-of-children 
	 in the case where several children end up in the same 
	 cell of their parent's array.
 
 Those fields are all integers (or arrays of integers) and are
 not represented by a class/struct: instead, they are implicitely
 represented as cells within an array of integers. 
 
 'nodeI' (note the final I) is the index of the first cell in a 
 node. The 'End' field can be found at `_nodes[nodeI + End]`, 
 and so on. 
 
 'nodeR' is the index of a cell holding a 'nodeI'. This is used
 by the algorithm when inserting new nodes, because nodeR is the
 index of the cell referencing the node (and is the cell which 
 has to be changed). 
 
 Hungarian: 
   Values 'iXXX' represent the 'input' source data 
   Values 'bXXX' represent the 'buffer' trie data
 
*/
public final class Trie
{
	private static final int First = 0;
	private static final int Buffer = 1;
	private static final int Start = 2;
	private static final int End = 3;
	private static final int Reference = 4;
	private static final int NextSibling = 5;
	private static final int Children = 6;

	private final IntArrayList _nodes = new IntArrayList();

	/** 
	 Individual values indexed by their unique identifier, as generated
	 by the trie.
	 
	 
	 <code>CollectionAssert.AreEqual(t.Values[t.Hash(bytes, 0, bytes.Length)], bytes)</code>
	 
	*/
	public final List<byte[]> Values = new ArrayList<byte[]>();

	public Trie()
	{
		Values.add(new byte[0]);
		for (int i = 0; i < Children + HashSizeAtLength(0); ++i)
		{
			_nodes.add(0);
		}
	}

	/** 
	 Computes the size of the "Children" hashtable based on the 
	 length of the prefix at that node.
	 
	 
	 Hash table size decreases exponentially, reaching '1' at
	 length 8. This means that shorter strings use up more memory
	 to avoid 'NextSibling' traversal, while longer strings use
	 'NextSibling' traversal to lower memory usage.
	 
	*/
	private int HashSizeAtLength(int length)
	{
		if (length < 2)
		{
			return 256;
		}
		if (length < 7)
		{
			return 256 >> (length - 2);
		}
		return 1;
	}

	/** 
	 Reads bytes iBytes[iStart .. iEnd] and returns an unique identifier k
	 for that sequence, such that <code>trie.Values[k]</code> is the 
	 corresponding sequence.
	*/
	public int Hash(byte[] iBytes, int iStart, int iEnd)
	{
		if (iEnd == iStart)
		{
			return 0;
		}

		// The initial values match the contents of the root node
		// (no point in reading them again: all zeros, never change)
		int bEnd = 0;
		int bStart = 0;
		int bPos = 0;
		int bFirstBytes = 0;

		// Only filled when a byte is needed and not found in 
		// the (more performant) bFirstBytes
		byte[] bBytes = null;

		int nodeI = 0;
		int nodeR = 0;

		for (int iPos = iStart; iPos < iEnd; ++iPos)
		{
			int iByte = UnsignedBytes.toInt(iBytes[iPos]);

			if (bPos == bEnd)
			{
				int hashSize = HashSizeAtLength(iPos - iStart);

				int childR = nodeI + Children + (iByte % hashSize);
				int childI = _nodes.getInt(childR);

				// Traverse the list of siblings looking for the one with the right
				// initial byte.
				while (childI != 0)
				{
					bFirstBytes = _nodes.getInt(childI + First);
					if (bFirstBytes % 256 == iByte)
					{
						break;
					}
					childR = childI + NextSibling;
					childI = _nodes.getInt(childR);
				}

				// This node does not have a child starting with the next byte: 
				// add a new child.
				if (childI == 0)
				{
					return AddNewChild(childR, iBytes, iStart, iEnd, iPos);
				}

				nodeI = childI;
				nodeR = childR;
				bStart = _nodes.getInt(nodeI + Start);
				bEnd = _nodes.getInt(nodeI + End);

				// The sibling search has already ensured that the first byte of 
				// the buffer matches: continue search from next position.
				bPos = bStart + 1;

				continue;
			}

			// Read the next character from bFirstBytes if possible, 
			// and otherwise from the buffer.

			int bByte;
			int bOffset = bPos - bStart;
			if (bOffset < 4)
			{
				bByte = (bFirstBytes >> (bOffset * 8)) % 256;
			}
			else
			{
				if (bOffset == 4)
				{
					bBytes = Values.get(_nodes.getInt(nodeI + Buffer));
				}

				// ReSharper disable once PossibleNullReferenceException
				//   bBytes will be initialized at offset = 4 and is not used before
				bByte = UnsignedBytes.toInt(bBytes[bPos]);
			}

			// Continue reading through the buffer while we match
			if (bByte == iByte)
			{
				bPos++;
				continue;
			}

			// A mismatch: we need to create a new node and insert it here as a
			// child of the current node.

			return AddNewNode(nodeI, nodeR, iBytes, iStart, iEnd, iPos, bPos);
		}

		// We reached the end of the input bytes without a conflict with the trie
		// structure: all we need to do is extract the reference from the current 
		// node, or insert the reference if it isn't already present.

		if (bEnd > bPos)
		{
			return AddNewEnd(nodeI, nodeR, iBytes, iStart, iEnd, bPos);
		}

		int reference = _nodes.getInt(nodeI + Reference);

		if (reference == 0)
		{
			int res = AddNewReference(iBytes, iStart, iEnd);
			_nodes.set(nodeI + Reference, res);
			return res;
			// Old return _nodes.set(nodeI + Reference, AddNewReference(iBytes, iStart, iEnd));
		}

		return reference;
	}

	/** 
	 Returns the first 4 bytes encoded as an integer.
	*/
	private int GetFirst(byte[] bytes, int pos)
	{
		int result = UnsignedBytes.toInt(bytes[pos++]);

		for (int i = 1; i < 4 && pos < bytes.length; ++i)
		{
			result = result + (UnsignedBytes.toInt(bytes[pos++]) << (i * 8));
		}

		return result;
	}

	/** 
	 Insert a new node into the specified node based on the provided position
	 within both input and node buffer.
	*/
	private int AddNewNode(int nodeI, int nodeR, byte[] iBytes, int iStart, int iEnd, int iPos, int bPos)
	{
		int bBytesI = _nodes.getInt(nodeI + Buffer);
		byte[] bBytes = Values.get(bBytesI);

		// Create the middle node
		// ======================

		int midI = _nodes.size();
		int midLength = iPos - iStart;
		int midHashSize = HashSizeAtLength(midLength);

		_nodes.add(_nodes.getInt(nodeI + First)); // First
		_nodes.add(bBytesI); // Buffer
		_nodes.add(_nodes.getInt(nodeI + Start)); // Start
		_nodes.add(bPos); // End
		_nodes.add(0); // Reference
		_nodes.add(_nodes.getInt(nodeI + NextSibling)); // NextSibling

		for (int u = 0; u < midHashSize; ++u)
		{
			_nodes.add(0); // Children
		}

		_nodes.set(midI + Children + (UnsignedBytes.toInt(bBytes[bPos]) % midHashSize), nodeI);

		// Replace the old node with the middle node
		// =========================================

		_nodes.set(nodeR, midI);

		// Update the old node
		// ===================

		_nodes.set(nodeI + First, GetFirst(bBytes, bPos));
		_nodes.set(nodeI + Start, bPos);
		_nodes.set(nodeI + NextSibling, 0);

		// Insert the new child
		// ====================

		int childR = midI + Children + (UnsignedBytes.toInt(iBytes[iPos]) % midHashSize);

		return AddNewChild(childR, iBytes, iStart, iEnd, iPos);
	}

	/** 
	 Insert a new end into the specified node based on the provided position. 
	*/
	private int AddNewEnd(int nodeI, int nodeR, byte[] iBytes, int iStart, int iEnd, int bPos)
	{
		int reference = AddNewReference(iBytes, iStart, iEnd);

		int length = iEnd - iStart;
		int midHashSize = HashSizeAtLength(length);

		int bBytesI = _nodes.getInt(nodeI + Buffer);
		byte[] bBytes = Values.get(bBytesI);

		// Create the middle node
		// ======================

		int midI = _nodes.size();

		_nodes.add(_nodes.getInt(nodeI + First)); // First
		_nodes.add(bBytesI); // Buffer
		_nodes.add(_nodes.getInt(nodeI + Start)); // Start
		_nodes.add(bPos); // End
		_nodes.add(reference); // Reference
		_nodes.add(_nodes.getInt(nodeI + NextSibling)); // NextSibling

		for (int u = 0; u < midHashSize; ++u) // Children
		{
			_nodes.add(0);
		}

		_nodes.set(midI + Children + UnsignedBytes.toInt(bBytes[bPos]) % midHashSize, nodeI);

		// Replace the old node with the middle node
		// =========================================

		_nodes.set(nodeR, midI);

		// Update the old node
		// ===================

		_nodes.set(nodeI + First, GetFirst(bBytes, bPos));
		_nodes.set(nodeI + Start, bPos);
		_nodes.set(nodeI + NextSibling, 0);

		return reference;
	}

	/** 
	 Create a new node as a child of 'nodeI' and containing 'iBytes[iPos..iEnd)' 
	 and pointing to the appropriate new buffer.
	 
	 
	 The new child has _nodes[childR] as its next sibling, and becomes the first 
	 child of its parent in cell childR.
	 
	*/
	private int AddNewChild(int childR, byte[] iBytes, int iStart, int iEnd, int iPos)
	{
		int reference = AddNewReference(iBytes, iStart, iEnd);
		int hashSize = HashSizeAtLength(iEnd - iStart);

		int childI = _nodes.size();

		_nodes.add(GetFirst(iBytes, iPos)); // First
		_nodes.add(reference); // Buffer
		_nodes.add(iPos - iStart); // Start
		_nodes.add(iEnd - iStart); // End
		_nodes.add(reference); // Reference
		_nodes.add(_nodes.getInt(childR)); // NextSibling

		for (int u = 0; u < hashSize; ++u) // Children
		{
			_nodes.add(0);
		}

		_nodes.set(childR, childI);

		return reference;
	}

	/** 
	 Extracts the referenced bytes and gives them an integer identifier.
	*/
	private int AddNewReference(byte[] iBytes, int iStart, int iEnd)
	{
		int length = iEnd - iStart;
		byte[] bBytes = new byte[length];
		System.arraycopy(iBytes, iStart, bBytes, 0, length);

		
		int buffer = Values.size();
		Values.add(bBytes);

		return buffer;
	}
}