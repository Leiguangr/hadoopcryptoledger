/**
* Copyright 2017 ZuInnoTe (Jörn Franke) <zuinnote@gmail.com>
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
**/
package org.zuinnote.hadoop.ethereum.format.common;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.zuinnote.hadoop.ethereum.format.exception.EthereumBlockReadException;

/**
 * This class parses Ethereum RLP-encoded blocks
 *
 */
public class EthereumBlockReader {
	
	private static final Log LOG = LogFactory.getLog(EthereumBlockReader.class.getName());
	private InputStream in;
	private int bufferSize;
	private int maxSizeEthereumBlock;
	private boolean useDirectBuffer;
	private ByteBuffer preAllocatedDirectByteBuffer;
	
	
	/**
	 * 
	 * 
	 */
	private EthereumBlockReader() {
		
	}
	
	/**
	 * 
	 * Initialize a reader
	 * 
	 * @param in InputStream containing Ethereum blocks
	 * @param maxSizeEthereumBlock maximum size of an Ethereum block for processing. This is to avoid misinterpretation or overflows when reading blocks (cf. also gas limit, https://github.com/ethereum/wiki/wiki/Design-Rationale#gas-and-fees)
	 * @param bufferSize read Buffer size. If set to 0 then the InputStream passsed as a parameter will be used
	 * @param useDirectBuffer experimental feature to use a DirectByteBuffer instead of a HeapByteBuffer
	 *
	 */
	public EthereumBlockReader(InputStream in, int maxSizeEthereumBlock, int bufferSize, boolean useDirectBuffer) {
		this.bufferSize=bufferSize;
		this.maxSizeEthereumBlock=maxSizeEthereumBlock;
		this.useDirectBuffer=useDirectBuffer;
		if (this.bufferSize<=0) {
			this.in=in;
		} else {
			this.in=new BufferedInputStream(in, this.bufferSize);
		}
		if (this.useDirectBuffer) { // in case of a DirectByteBuffer we do allocation only once for the maximum size of one block, otherwise we will have a high cost for reallocation
			this.preAllocatedDirectByteBuffer=ByteBuffer.allocateDirect(this.maxSizeEthereumBlock);
		}
	}
	
	/*
	 * 
	 * Read a block into a Java object of the class Ethereum Block. This makes analysis very easy, but might be slower for some type of analytics where you are only interested in small parts of the block. In this case it is recommended to use {@link #readRawBlock}
	 * @return
	 */
	
	public EthereumBlock readBlock() {
		return null;
	}
	
	/*
	 * Reads a raw Ethereum block into a ByteBuffer. This method is recommended if you are only interested in a small part of the block and do not need the deserialization of the full block, ie in case you generally skip a lot of blocks
	 * 
	 * @return
	 */
	public ByteBuffer readRawBlock() throws IOException, EthereumBlockReadException {
		// basically an Ethereum Block is simply a RLP encoded list
		ByteBuffer result=null;
		// get size of list
		this.in.mark(10);
		byte[] listHeader = new byte[10];
		this.in.read(listHeader);
		ByteBuffer sizeByteBuffer=ByteBuffer.wrap(listHeader);
		long blockSize = EthereumUtil.getRLPListSize(sizeByteBuffer); // gets block size including indicator
		this.in.reset();
		// check if blockSize is valid
		if (blockSize==0) {
			throw new EthereumBlockReadException("Error: Blocksize too small");
		}
		if (blockSize<0) {
			throw new EthereumBlockReadException("Error: This block size cannot be handled currently (larger then largest number in positive signed int)");
		}
		if (blockSize>this.maxSizeEthereumBlock) {
			throw new EthereumBlockReadException("Error: Block size is larger then defined in configuration - Please increase it if this is a valid block");
		}
		// read block
		int blockSizeInt=(int)(blockSize);
		byte[] fullBlock=new byte[blockSizeInt];
		int totalByteRead=0;
		int readByte;
		while ((readByte=this.in.read(fullBlock,totalByteRead,blockSizeInt-totalByteRead))>-1) {
				totalByteRead+=readByte;
				if (totalByteRead>=blockSize) {
					break;
				}
		}
		if (totalByteRead!=blockSize) {
			 throw new EthereumBlockReadException("Error: Could not read full block");
		}
		if (!(this.useDirectBuffer)) {
		 	result=ByteBuffer.wrap(fullBlock);	
		} else {
			preAllocatedDirectByteBuffer.clear(); // clear out old bytebuffer
			preAllocatedDirectByteBuffer.limit(fullBlock.length); // limit the bytebuffer
			result=preAllocatedDirectByteBuffer;
			result.put(fullBlock);
			result.flip(); // put in read mode
		}
		result.order(ByteOrder.LITTLE_ENDIAN);	
		return result;
	}
	
	/** 
	 *  Closes the Ethereum Block reader
	 * 
	 */
	public void close() throws IOException {
		if (this.in!=null) {
			this.in.close();
		}
	}
}
