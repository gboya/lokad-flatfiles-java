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
		
		// Test output
		assertArrayEquals(expectedBytes, outputStream.toByteArray());
	}
	
	@Parameters
	public static Collection<Object[]> testCases()
	{
		return Arrays.asList(new Object[][] {
				{"/Lokad_Items.tsv", "/Lokad_Items.rff"},
				{"/Lokad_Orders.tsv", "/Lokad_Orders.rff"},
				{"/Lokad_Items_UTF8_quotes.tsv", "/Lokad_Items_UTF8_quotes.rff"}
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
