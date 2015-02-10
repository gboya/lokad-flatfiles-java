package com.lokad.flatfiles;

import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public final class Program
{
	static public void main(String[] args) throws IOException
	{
		
		String inputFile = args[0];
		String outputFile = args[1];

		FileInputStream input = null;
		FileOutputStream output = null;
		try 
		{
			// Parse the input file
			input = new FileInputStream(new File(inputFile));
			RawFlatFile rff = new RawFlatFile(new DataInputStream(input));
			
			// Write the compressed output file
			output = new FileOutputStream(new File(outputFile));
			BufferedOutputStream streamBuff = new BufferedOutputStream(output);
			RawFlatFileSerialization.Write(new DataOutputStream(streamBuff), rff);
			
		} catch ( Exception e) {
			e.printStackTrace();
		} finally {
			if ( input != null) {
				input.close();
			}
			
			if ( output != null) {
				output.close();
			}
		}
		
	}
}