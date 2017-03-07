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

	public DatagramPacket previous_package = null;
	public static InetSocketAddress socketAddress;
	// requestedFile variable moved to fields to allow access in ParseRQ method
	public static StringBuffer requestedFile;
	public String transferMode;
	public static int opcode;
	public static int currentSize = 0;
	public int lastPackage_length = BUFSIZE;	
	public short block = 1;

	public boolean endHandling = false;
	public boolean endHandlingFlag = false;
	private int timesRetransmitted = 0;
	private int max;
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

			socketAddress = new InetSocketAddress(receivePackage.getAddress(), receivePackage.getPort());

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (Exception e) {
			e.getMessage();
		}

		// Get client address and port from the packet

		return socketAddress;
	}

	/**
	 * Parses the request in buf to retrieve the type of request and
	 * requestedFile
	 * 
	 * @param buf
	 *            (received request)
	 * @param requestedFile
	 *            (name of file to read/write)
	 * @return opcode (request type: RRQ or WRQ)
	 */
	private int ParseRQ(byte[] buf) {
		// ByteBuffer used to get the first 2 bytes and convert it a short which
		// represents the opcode.
		ByteBuffer wrap = ByteBuffer.wrap(buf);
		short opcode = wrap.getShort();

		System.out.println("Opcode: " + opcode);

		int nameLength = 0;

		// for loop to find the where in the byte array the name of the
		// requested file ends.
		for (int i = 2; i < buf.length; i++) {

			// if element is == 0 then the name of the requested file has ended.
			if (buf[i] == 0) {

				nameLength = i;
				i = buf.length;
			}

		}

		// create a string which contains the name of the requested file using
		// variable found in for loop to locate end.
		String fileName = new String(buf, 2, nameLength - 2);

		int transferTypeLength = 0;

		// for loop to find where in the bite array the transfer type ends.
		for (int i = nameLength + 1; i < buf.length; i++) {

			// if element is 0 we have found the length of the transfer type
			if (buf[i] == 0) {

				transferTypeLength = i;
				i = buf.length;
			}

		}

		// create string which contains name of the transfer type using
		// variables found in for loop to locate
		transferMode = new String(buf, nameLength + 1, transferTypeLength - nameLength - 1);

		// set name of requested file to be used in the run method
		requestedFile = new StringBuffer(fileName);

		System.out.println(requestedFile);
		System.out.println(transferMode);

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
		
		if (opcode == OP_RRQ) {
			try{
				// Create file to read from requested file
				File file = new File(requestedFile);
				// File input stream to read bytes into the temporary buffer
				// holding the content
				FileInputStream stream = new FileInputStream(file);
				
				byte[] data = new byte[(int) file.length()+1];
				byte[] temp = new byte[BUFSIZE - 4];
				// not needed only for testing purpose
				// temp = requestedFile.getBytes();

				/*
				 * Read into the temporary buffer an stores it in a string in
				 * order to see the output
				 */
				try {
					//Read
					stream.read(data);		
					while(!endHandling){
						
						int tempCounter = 0;
						
						for(int i = temp.length*(block-1); i < temp.length*block; i++){
							
							if(data[i] == 0){
								endHandlingFlag = true;
								lastPackage_length = tempCounter + 4;
								break;
							}
							
							temp[tempCounter] = data[i];
							tempCounter++;
							
						}
						
						tempCounter = 0;
						
						// See "TFTP Formats" in TFTP specification for the DATA and ACK
						// packet contents
						boolean result = send_DATA_receive_ACK(sendSocket, response, temp, this.block);
						
						temp = new byte[BUFSIZE - 4];
	
						// Presumably if result returns false we will send a error 
						if (!result) {
							max++;
							if(max>=5){
								TimeUnit.MILLISECONDS.sleep(5);
								send_ERR(sendSocket,(short) 0, TFTP_ERROR_0);
								endHandling = true;
								// delay before end
							}
						}
					}
					
					System.out.println(timesRetransmitted);
					endHandling = false;
					endHandlingFlag = false;
					lastPackage_length = BUFSIZE;
					stream.close();	
				} catch (IOException e) {
					send_ERR(sendSocket,(short) 0, TFTP_ERROR_0);
					e.printStackTrace();
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
			} catch (FileNotFoundException e) {
				send_ERR(sendSocket,(short) 1, TFTP_ERROR_1);
				e.printStackTrace();
			}

		} else if (opcode == OP_WRQ) {
			File createFile = new File(requestedFile);
			FileOutputStream stream;
			try {
				if(createFile.exists() && !createFile.isDirectory()){
					send_ERR(sendSocket, (short) 6, TFTP_ERROR_6);
					System.out.println("File already exist!");
				} else {
					stream = new FileOutputStream(createFile);
					
					while(!endHandling){
						boolean result = receive_DATA_send_ACK(sendSocket, response, stream);

						if(!result){
							max++;
							if(max>=5){
								TimeUnit.MILLISECONDS.sleep(5);
								send_ERR(sendSocket,(short) 0, TFTP_ERROR_0);
								endHandling = true;
								// delay before end
							}
						}
						
						response = new byte[BUFSIZE];
					}

					System.out.println(timesRetransmitted);
					endHandling = false;
					endHandlingFlag = false;
					lastPackage_length = BUFSIZE;
					stream.close();
				}
			} catch (IOException e) {
				send_ERR(sendSocket,(short) 0, TFTP_ERROR_0);
				e.printStackTrace();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} else {
			System.err.println("Access violation!");
			send_ERR(sendSocket,(short) 2, TFTP_ERROR_2);
			return;
		}
	}

	/**
	 * To be implemented
	 */
	private boolean send_DATA_receive_ACK(DatagramSocket sendSocket, byte[] buf, byte[] data, short block) {
		// short value for 2 bytes
		short shortVal = OP_DAT;
		short val = block;

		// Allocating to the buf
		ByteBuffer wrap = ByteBuffer.wrap(buf);
		// put the short to the bytebuffer
		wrap.putShort(shortVal);
		wrap.putShort(val);
		wrap.put(data);
		
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
			// For the ACK package need only 4 bytes, two for opcode and two for
			// block code
			byte[] received = new byte[4];
			DatagramPacket recievedACK = new DatagramPacket(received, received.length);

			sendSocket.send(sendPackage);
			sendSocket.setSoTimeout(ACKTIME);
			try{
			sendSocket.receive(recievedACK);
			} catch(IOException e){
				timesRetransmitted++;
				e.printStackTrace();
				return false;
			}
			if(received[1] == 4){
				if(this.block == received[3]){
					System.out.println("Acknowledgment: block " + block + " received");
					this.block++;
					this.max = 0;
				}
				else{
					timesRetransmitted++;
				}
				
				
				if(endHandlingFlag){
					endHandling = true;
					this.block = 1;
				}
				return true;
			}
			else {
				System.err.println("Could not send data!");
				return false;
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return false;
		}
	}

	private boolean receive_DATA_send_ACK(DatagramSocket sendSocket, byte[] buf, FileOutputStream file) {
		DatagramPacket receivePackage = new DatagramPacket(buf, buf.length);
		
		try{
			if(this.block == 1){
				// For the ACK package need only 4 bytes, two for opcode and two for block code
				byte[] start = new byte[4];
				DatagramPacket startACK = new DatagramPacket(start, start.length);
				ByteBuffer wrap = ByteBuffer.wrap(start);
				wrap.putShort((short) OP_ACK);
				wrap.putShort((short) 0);
				sendSocket.send(startACK);
			}
			
			sendSocket.setSoTimeout(5);
			sendSocket.setSoTimeout(ACKTIME);
			try{
			sendSocket.receive(receivePackage);
			} catch(IOException e){
				timesRetransmitted++;
				e.printStackTrace();
				return false;
			}
			System.out.println("NULL=?" + buf[buf.length - 1]);
			if(buf[3] == this.block){
				// For the ACK package need only 4 bytes, two for opcode and two for block code
				byte[] send = new byte[4];
				DatagramPacket dataACK = new DatagramPacket(send, send.length);
				ByteBuffer wrap2 = ByteBuffer.wrap(send);
				wrap2.putShort((short) OP_ACK);
				wrap2.putShort((short) this.block);
				
				if(buf[buf.length - 1] == 0){
					System.out.println("IS THIS IT?");
					
					for(int i = 4; i < buf.length - 4; i++){
						if(buf[i] == 0){
							lastPackage_length = i - 1;
						}
					}
					endHandling = true;
					this.block = 1;

					sendSocket.send(dataACK);
				}

				ByteBuffer receive = ByteBuffer.wrap(buf);
				short opcode = receive.getShort();
				short block = receive.getShort();

				System.out.println("opcode "+ opcode);
				System.out.println("block: " +block);
				if(opcode == OP_WRQ){
					System.err.println("Not write request..");
					return false;
				}else{
					String rec = new String(buf, 4, lastPackage_length - 4);
					file.write(buf, 4, lastPackage_length - 4);
					System.out.println(rec);

					this.block++;
					this.max = 0;

					sendSocket.send(dataACK);
					return true;	
				}
			} else {
				timesRetransmitted++;

				return false;
			}
		} catch(IOException e){
			e.getMessage();
			return false;
		}
	}

	private void send_ERR(DatagramSocket sendSocket, short errorcode, String errormsg) {
		System.out.println("5");
		byte[] sendError = new byte[BUFSIZE - 4];
		// short value for 2 bytes
		short shortVal = OP_ERR;
		short val = errorcode;
		System.out.println(shortVal);
		System.out.println(errorcode);

		// Allocating to the buf
		ByteBuffer wrap = ByteBuffer.wrap(sendError);
		wrap.putShort(shortVal);
		wrap.putShort(val);
		stringToByte(errormsg, sendError);
		wrap.put((byte)0);
		
		DatagramPacket errorPackage = new DatagramPacket(sendError, sendError.length);
		try{
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
