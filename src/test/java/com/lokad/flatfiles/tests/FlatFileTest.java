package com.lokad.flatfiles.tests;

import static org.junit.Assert.*;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collection;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.lokad.flatfiles.RawFlatFile;
import com.lokad.flatfiles.RawFlatFileSerialization;


@RunWith(value = Parameterized.class)
public class FlatFileTest {

	private String sourceFile;
	private String expectedOutputFile;

	public FlatFileTest(String sourceFile, String expectedOutputFile) {
		this.sourceFile = sourceFile;
		this.expectedOutputFile = expectedOutputFile;
	}

	@Test
	public void compareGeneratedRffWithExpectedOutput() throws IOException {
		
		InputStream itemsInputStream = getClass().getResourceAsStream(sourceFile);
		InputStream expectedOutputStream = new DataInputStream(getClass().getResourceAsStream(expectedOutputFile));
		byte[] expectedBytes = toByteArray(expectedOutputStream);		
		
		// Perform serialization
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		RawFlatFile rff = new RawFlatFile(new DataInputStream(itemsInputStream));
		RawFlatFileSerialization.Write(new DataOutputStream(outputStream), rff);		
		
		byte[] outputBytes = outputStream.toByteArray();
		// Test output
		try {
			assertArrayEquals(expectedBytes, outputBytes);
		} catch(AssertionError ae) {
			// Find where it differs
			for(int i = 0; i < Math.min(expectedBytes.length, outputBytes.length); i++) {
				if(outputBytes[i] != expectedBytes[i]) {
					System.out.println("Bytes arrays differ at index : " + i + ". Expecting : " + expectedBytes[i] + " but was : " + outputBytes[i]);
				}
			}
			throw ae;
		}
	}
	
	@Parameters
	public static Collection<Object[]> testCases()
	{
		return Arrays.asList(new Object[][] {
				{"/Lokad_Items.tsv", "/Lokad_Items.rff"},
				{"/Lokad_Orders.tsv", "/Lokad_Orders.rff"},
				{"/Lokad_Items_UTF8_quotes.tsv", "/Lokad_Items_UTF8_quotes.rff"},
				{"/Lokad_Items_UTF8_quotes_small.tsv", "/Lokad_Items_UTF8_quotes_small.rff"},
				{"/Lokad_Items_UTF8_noquotes_small.tsv", "/Lokad_Items_UTF8_noquotes_small.rff"},
				{"/Lokad_Items_UTF8_tiny.tsv", "/Lokad_Items_UTF8_tiny.rff"},
				{"/No_EOL_at_EOF.tsv", "/No_EOL_at_EOF.rff"},
				{"/Long_Prefix.tsv", "/Long_Prefix.rff"},
		});
	}

	/**
	 * Helper to get a byte array from a input stream
	 */
    private static byte[] toByteArray(InputStream is) throws IOException{
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int reads = is.read();
       
        while(reads != -1){
            baos.write(reads);
            reads = is.read();
        }
      
        return baos.toByteArray();
    }

}
