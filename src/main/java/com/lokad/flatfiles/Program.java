package com.lokad.flatfiles;

import java.io.BufferedInputStream;
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
		if(args.length == 0) {
			System.out.println("=== USAGE : ===");
			System.out.println();
			System.out.println("1. Creating a RawFlatFile from a TSV/CSV file : ");
			System.out.println("java -jar lokad-flat-files.jar InputFile.tsv OutputFile.rff");
			System.out.println();
			System.out.println("2. Validating an existing RawFlatFile : ");
			System.out.println("java -jar lokad-flat-files.jar -check RawFlatFile.rff");
			
			return;
		}
		
		// Case 2 : Checking consistency of an existing RawFlatFile		
		if (args[0].equals("-check")) {
			String rffPath = args[1];
			try (FileInputStream file = new FileInputStream(rffPath);
					// Using a BufferedInputStream here improves performance dramatically
					DataInputStream in = new DataInputStream(new BufferedInputStream(file))) {
				RawFlatFile rff = RawFlatFileSerialization.ReadRawFlatFile(in);
				rff.ThrowIfInconsistent();
				System.out.println("File : " + rffPath + " is well formed.");
			} catch (Exception e) {
				e.printStackTrace();
			}
			return;
		}
		
		
		// Case 1 : Converting a TSV file to a RawFlatFile
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