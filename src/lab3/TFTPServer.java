/*
 * File:	TFTPServer.java 
 * Course: 	Computer Network
 * Code: 	IDV701
 * Author: 	Christofer Nguyen & Jonathan Walkden
 * Date: 	February, 2017
 */

package lab3;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

public class TFTPServer {
	public static final int TFTPPORT = 4970;
	public static final int BUFSIZE = 516;
	public static final String READDIR = "Directory/ReadDir/"; // custom address at your PC
	public static final String WRITEDIR = "Directory/WriteDir/"; 
	public static final int OP_RRQ = 1;
	public static final int OP_WRQ = 2;
	public static final int OP_DAT = 3;
	public static final int OP_ACK = 4;
	public static final int OP_ERR = 5;

	public static final String TFTP_ERROR_0 = "Not defined!";
	public static final String TFTP_ERROR_1 = "File not found";
	public static final String TFTP_ERROR_2 = "Access violation";
	public static final String TFTP_ERROR_6 = "File already exists";

	public static InetSocketAddress socketAddress;
	
	// requestedFile variable moved to fields to allow access in ParseRQ method
	public static StringBuffer requestedFile;
	
	public String transferMode;
	
	public short block = 1;

	public boolean endHandling = false;
	public boolean endHandlingFlag = false;
	public int lastPackage_length = BUFSIZE;
	
	// "timesRestransmitted" keeps track of how many times blocks had to be retransmitted
	private int timesRetransmitted = 0;
	
	// "max" variable keeps track of how many times a single packet didn't recieve acknowledgment
	private int max;
	
	//time in milliseconds that socket will be recieving for
	private final int ACKTIME = 3;
	
	public static void main(String[] args) {
		if (args.length > 0) {
			System.err.printf("usage: java %s\n", TFTPServer.class.getCanonicalName());
			System.exit(1);
		}
		// Starting the server
		try {
			TFTPServer server = new TFTPServer();
			server.start();
		} catch (SocketException e) {
			e.printStackTrace();
		}
	}

	private void start() throws SocketException {
		byte[] buf = new byte[BUFSIZE];

		// Create socket
		DatagramSocket socket = new DatagramSocket(null);

		// Create local bind point
		SocketAddress localBindPoint = new InetSocketAddress(TFTPPORT);
		socket.bind(localBindPoint);

		System.out.printf("Listening at port %d for new requests\n", TFTPPORT);

		// Loop to handle client requests
		while (true) {

			final InetSocketAddress clientAddress = receiveFrom(socket, buf);

			// If clientAddress is null, an error occurred in receiveFrom()
			if (clientAddress == null) {
				continue;
			}
			
			// get the type of request (read or write)
			final int reqtype = ParseRQ(buf);

			new Thread() {
				public void run() {
					try {
						DatagramSocket sendSocket = new DatagramSocket(0);

						// Connect to client
						sendSocket.connect(clientAddress);

						System.out.printf("%s request for %s from %s using port %d\n",
								(reqtype == OP_RRQ) ? "Read" : "Write", requestedFile, clientAddress.getHostName(),
										clientAddress.getPort());

						// Read request
						if (reqtype == OP_RRQ) {
							requestedFile.insert(0, READDIR);
							HandleRQ(sendSocket, requestedFile.toString(), OP_RRQ);
							System.out.println("Handle method done!");
						}
						// Write request
						else {
							requestedFile.insert(0, WRITEDIR);
							HandleRQ(sendSocket, requestedFile.toString(), OP_WRQ);
						}
						sendSocket.close();
					} catch (SocketException e) {
						e.printStackTrace();
					}
				}
			}.start();
		}
	}

	/**
	 * Reads the first block of data, i.e., the request for an action (read or
	 * write).
	 * 
	 * @param socket
	 *            (socket to read from)
	 * @param buf
	 *            (where to store the read data)
	 * @return socketAddress (the socket address of the client)
	 */
	private InetSocketAddress receiveFrom(DatagramSocket socket, byte[] buf) {
		// Create datagram packet
		DatagramPacket receivePackage = new DatagramPacket(buf, buf.length);
		
		// Receive packet
		try {
			System.out.println("Receiving..");
			socket.receive(receivePackage);
			
			// Get client address and port from the packet
			socketAddress = new InetSocketAddress(receivePackage.getAddress(), receivePackage.getPort());

		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return socketAddress;
	}

	/**
	 * Parses the request in buf to retrieve the type of request and
	 * requestedFile
	 * 
	 * @param buf
	 *            (received request)
	 * @return opcode (request type: RRQ or WRQ)
	 */
	private int ParseRQ(byte[] buf) {
		
		/* ByteBuffer used to get the first 2 bytes and convert
		 *  it to a short which represents the opcode.*/
		ByteBuffer wrap = ByteBuffer.wrap(buf);
		short opcode = wrap.getShort();
		
		// nameLength variable used to parse the name of file from request
		int nameLength = 0;

		/* for loop to find the where in the byte array
		 *  the name of the requested file ends.*/
		for (int i = 2; i < buf.length; i++) {

			// if element is == 0 then the name of the requested file has ended.
			if (buf[i] == 0) {

				nameLength = i;
				i = buf.length;
			}

		}

		/* create a string which contains the name of the requested file 
		 * using nameLength variable to locate end.*/
		String fileName = new String(buf, 2, nameLength - 2);
		
		// transferTypeLength variable used to parse the transfer type of file from request
		int transferTypeLength = 0;

		// for loop to find where in the bite array the transfer type ends.
		for (int i = nameLength + 1; i < buf.length; i++) {

			// if element is 0 we have found the length of the transfer type
			if (buf[i] == 0) {

				transferTypeLength = i;
				i = buf.length;
			}

		}

		/* create string which contains name of the transfer 
		 * type using variables found in for loop to locate*/
		transferMode = new String(buf, nameLength + 1, transferTypeLength - nameLength - 1);

		// set name of requested file to be used in the run method
		requestedFile = new StringBuffer(fileName);

		System.out.println("File name: " + requestedFile);
		System.out.println("Transfer type: " + transferMode);

		return opcode;
	}

	/**
	 * Handles RRQ and WRQ requests
	 * 
	 * @param sendSocket
	 *            (socket used to send/receive packets)
	 * @param requestedFile
	 *            (name of file to read/write)
	 * @param opcode
	 *            (RRQ or WRQ)
	 */
	private void HandleRQ(DatagramSocket sendSocket, String requestedFile, int opcode) {
		
		// Response buffer
		byte[] response = new byte[BUFSIZE];
		
		// if read request is recieved.
		if (opcode == OP_RRQ) {
			try{
				
				// Create file to read from requested file
				File file = new File(requestedFile);
				
				// File input stream to read bytes into the temporary buffer holding the content
				FileInputStream stream = new FileInputStream(file);
				
				// array "data" used to hold all the bytes contained in the requested file
				byte[] data = new byte[(int) file.length()+1];
				
				// array "temp" used to hold the 512 bytes corresponding to the current block
				byte[] temp = new byte[BUFSIZE - 4];

				/*
				 * Read into the temporary buffer an stores it in a string in
				 * order to see the output
				 */
				try {
					//Read
					stream.read(data);
					
					// while loop that continues handling the request for as long as there are block to be sent
					while(!endHandling){
						
						int tempCounter = 0;
						
						// for loop that gets the 512 bytes from the current block in the data array 
						for(int i = temp.length*(block-1); i < temp.length*block; i++){
							
							// if element in the data array is equal to 0 then there is no more text to be sent
							if(data[i] == 0){
								
								/* sets flag to true so that while loop can be broken
								* once it is confirmed there are no resubmissions required*/
								endHandlingFlag = true;
								
								/* "lastPackage_length" variable which is the size of the last packet to be sent.
								 * +4 to account for opcode and block number.
								 */
								lastPackage_length = tempCounter + 4;
								break;
							}
							
							// transfer elements from data array current block to temp array to be sent.
							temp[tempCounter] = data[i];
							tempCounter++;
							
						}
						
						tempCounter = 0;
						
						// sends packet to client with this method
						boolean result = send_DATA_receive_ACK(sendSocket, response, temp, this.block);
						
						temp = new byte[BUFSIZE - 4];
	
						// if result returns false, no acknowledgement recieved, we will send a error if "max" variable is met
						if (!result) {
							
							// "max" variable signifies how many times a single packet has been sent without acknowledgment
							max++;
							
							/* if max is equal to 5 then something is wrong and packets arent being acknowledged,
							 * send error and stop handling.*/
							if(max>=5){
								// delay before end
								TimeUnit.MILLISECONDS.sleep(5);
								send_ERR(sendSocket,(short) 0, TFTP_ERROR_0);
								System.out.println("Packets aren't being acknowledged, error sent and process stopped");
								endHandling = true;
								
							}
						}
					}
					
					stream.close();	
				} catch (IOException e) {
					send_ERR(sendSocket,(short) 0, TFTP_ERROR_0);
					e.printStackTrace();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				
			} catch (FileNotFoundException e) {
				send_ERR(sendSocket,(short) 1, TFTP_ERROR_1);
				e.printStackTrace();
			}

		} 
		
		// if write request is recieved.
		else if (opcode == OP_WRQ) {
			
			// Create file to read into
			File createFile = new File(requestedFile);
			FileOutputStream stream;
			
			try {
				
				// check if file exists or is a directory and send error 6 if true.
				if(createFile.exists() && !createFile.isDirectory()){
					
					send_ERR(sendSocket, (short) 6, TFTP_ERROR_6);
					System.out.println("File already exist!");
					
				} 
				
				else {
					
					// create file stream to write sent data into
					stream = new FileOutputStream(createFile);
					
					// while loop that continues handling the request for as long as there are block to be recieved
					while(!endHandling){
						
						// recieve data to be placed in text file from client 
						boolean result = receive_DATA_send_ACK(sendSocket, response, stream);
						
						// if result returns false, no data recieved, we will send a error if "max" variable is met
						if (!result) {
							
							// "max" variable signifies how many times a single packet has not been received by the server
							max++;
							
							/* if max is equal to 5 then something is wrong and packets arent being received,
							 * send error and stop handling.*/
							if(max>=5){
								// delay before end
								TimeUnit.MILLISECONDS.sleep(5);
								send_ERR(sendSocket,(short) 0, TFTP_ERROR_0);
								endHandling = true;
								
							}
						}
						
						response = new byte[BUFSIZE];
					}
				}
				
			} catch (IOException e) {
				send_ERR(sendSocket,(short) 0, TFTP_ERROR_0);
				e.printStackTrace();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			
		} else {
			System.err.println("Access violation!");
			send_ERR(sendSocket,(short) 2, TFTP_ERROR_2);
			return;
		}
		
		System.out.println("Times blocks were retransmitted: " + timesRetransmitted);
		
		// once while loop has ended, reinitialze the variables to allow another request to be sent.
		endHandling = false;
		endHandlingFlag = false;
		lastPackage_length = BUFSIZE;
		timesRetransmitted = 0;
		this.block = 1;
		
	}
	
	/* method which sends data to client one block at a time and 
	 * waits for an acknowledgment that data was recieved by client*/ 
	private boolean send_DATA_receive_ACK(DatagramSocket sendSocket, byte[] buf, byte[] data, short block) {
		
		// "shortVal" contains value of opcode being sent
		short shortVal = OP_DAT;
		
		// "val" contains value of block number being sent
		short val = block;

		// ByteBuffer which will contain all data to be sent to client
		ByteBuffer wrap = ByteBuffer.wrap(buf);
		
		// put the opcode and block number in the bytebuffer
		wrap.putShort(shortVal);
		wrap.putShort(val);
		
		// places the data from the array sent in as parameter into the ByteBuffer to be sent
		wrap.put(data);
		
		// console print outs of text to be sent
		if(!endHandlingFlag){
			String send = new String(buf,4,BUFSIZE-4);
			System.out.println(send + "\n");
		}
		else if(endHandlingFlag){
			String send = new String(buf, 4, lastPackage_length-4);
			System.out.println(send + "\n");
		}
			
		// Send package
		DatagramPacket sendPackage = new DatagramPacket(wrap.array(), lastPackage_length);
		try {
			// the ACK package only needs 4 bytes, two for opcode and two for block code
			byte[] received = new byte[4];
			DatagramPacket recievedACK = new DatagramPacket(received, received.length);
			
			// send the package to the client
			sendSocket.send(sendPackage);
			
			// set time to to recieve acknowledgment
			sendSocket.setSoTimeout(ACKTIME);
			
			try{
				// start recieving
				sendSocket.receive(recievedACK);
			} catch(IOException e){
				
				/* if we reach this catch statement then it means the server did not recieve 
				 * an acknowledgment from the client in the set time we were recieving.
				 * Therefor we return false which will cause the handle method to 
				 * recall this method with the same block of bytes to be sent, to try again*/
				System.err.println("No acknowledgment recieved!");
				e.printStackTrace();
				return false;
			}
			
			// check to see if opcode recieved is equal to acknowledgment opcode
			if(received[1] == OP_ACK){
				
				/* check to see if block number is equal to sent block, 
				 * we will keep sending the same block everytime this method is entered, until it is acknowledged*/
				if(this.block == received[3]){
					System.out.println("Acknowledgment: block " + block + " received \n");
					
					// increment block number
					this.block++;
					this.max = 0;
					
					// if this is the last block to be sent flag will be set and we can end handling of request
					if(endHandlingFlag){
						endHandling = true;
					}
				}
				else{
					// block number isn't acknowledged then increment transmit counter
					timesRetransmitted++;
				}
				
				return true;
			}
			
			// if anything other than acknoledgment opcode is recieved then client is sending error and we should terminate request
			else {
				
				System.out.println("Client sent back error, request handling stopped.");
				endHandling = true;
				return false;
				
			}
			
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
	}
	
	/* method which recieves data from client one block at a time and 
	 * sends an acknowledgment that data was recieved by server*/ 
	private boolean receive_DATA_send_ACK(DatagramSocket sendSocket, byte[] buf, FileOutputStream file) {
		
		DatagramPacket receivePackage = new DatagramPacket(buf, buf.length);
		try{
			
			// on first package send acknowledgment opcode with block number 0 to confirm server is ready to recieve
			if(this.block == 1){
				
				// the ACK package only needs 4 bytes, two for opcode and two for block code
				byte[] start = new byte[4];
				DatagramPacket startACK = new DatagramPacket(start, start.length);
				
				ByteBuffer wrap = ByteBuffer.wrap(start);
				
				wrap.putShort((short) OP_ACK);
				wrap.putShort((short) 0);
				
				// send first package to initiate recieving.
				sendSocket.send(startACK);
			}
			
			// set time to to recieve package from client
			sendSocket.setSoTimeout(ACKTIME);
			
			try{
				// start recieving
				sendSocket.receive(receivePackage);
				
			} catch(IOException e){
				
				/* if we reach this catch statement then it means the server did not receive 
				 * any data from the client in the set time we were recieving.
				 * Therefor we return false which will cause the handle method to 
				 * recall this method to try again*/
				System.err.println("No package recieved!");
				e.printStackTrace();
				return false;
			}
			
			// check to see if block number in received package is equal to current block to be recieved
			if(buf[3] == this.block){
				
				// create acknowledgment package to send if we received the correct block number of data
				byte[] send = new byte[4];
				DatagramPacket dataACK = new DatagramPacket(send, send.length);
				
				ByteBuffer wrap2 = ByteBuffer.wrap(send);
				
				wrap2.putShort((short) OP_ACK);
				wrap2.putShort((short) this.block);
				
				// check to see if last element in received package is equal to zero, which signifies this is last package
				if(buf[buf.length - 1] == 0){
					
					// for loop to find length of last package
					for(int i = 4; i < buf.length - 4; i++){
						if(buf[i] == 0){
							lastPackage_length = i - 1;
						}
					}
					
					// endHandling sert to true to break while loop in handle method
					endHandling = true;
					
				}
				
				// create ByteBuffer which contains all data received in package from client
				ByteBuffer receive = ByteBuffer.wrap(buf);
				
				// get opcode sent by client
				short opcode = receive.getShort();
				
				// check to see that opcode is correct
				if(opcode != OP_DAT){
					
					System.err.println("Not write request..");
					return false;
				} 
				
				// if correct opcode then create string from data sent and place it in textfile we are wrting to
				else{
					String rec = new String(buf, 4, lastPackage_length - 4);
					file.write(buf, 4, lastPackage_length - 4);
					System.out.println(rec + " \n");
					
					// send acknowledgment to client that correct block of data has been received
					sendSocket.send(dataACK);
					System.out.println("Acknowledgment: block " + this.block + " of data written \n");
					
					// increment block number to receive next block of data from client
					this.block++;
					this.max = 0;
					
					return true;	
				}
			} 
			
			// if incorrect block has been recieved then return false and wait to receive current block when this method is called again
			else {
				
				timesRetransmitted++;
				return false;
			}
			
		} catch(IOException e){
			e.getMessage();
			return false;
		}
	}
	
	// method for sending errors to the client connected to our server
	private void send_ERR(DatagramSocket sendSocket, short errorcode, String errormsg) {
		
		// create array which holds error opcode and type of error number
		byte[] sendError = new byte[BUFSIZE - 4];
		short shortVal = OP_ERR;
		short val = errorcode;
		
		System.out.println("Error occured, error code: " + errorcode + " sent to client.");

		// create ByteBuffer containing error information to send to client
		ByteBuffer wrap = ByteBuffer.wrap(sendError);
		wrap.putShort(shortVal);
		wrap.putShort(val);
		stringToByte(errormsg, sendError);
		wrap.put((byte)0);
		
		DatagramPacket errorPackage = new DatagramPacket(sendError, sendError.length);
		try{
			
			// send error package
			sendSocket.send(errorPackage);
		} catch (IOException e){
			System.err.println("Problem with sending error..!");
			e.getMessage();
		}
	}
	
	/*
	 * Help method to change string to byte in order to be allocated into the buffer
	 */
	private void stringToByte(String s, byte[] buf){
		byte[] temp = new byte[s.length()];
		temp = s.getBytes();

		for(int i = 4; i < s.length(); i++){
			byte b = 0;

			for(int k = 0; k <= i; k++){
				b = temp[k];
			}
			buf[i] = b;
		}
	}
}
