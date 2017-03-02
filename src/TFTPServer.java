import java.io.File;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;

public class TFTPServer 
{
	public static final int TFTPPORT = 4970;
	public static final int BUFSIZE = 516;
	public static final String READDIR = "read/"; //custom address at your PC
	public static final String WRITEDIR = "write/"; //custom address at your PC
	// OP codes
	public static final int OP_RRQ = 1;
	public static final int OP_WRQ = 2;
	public static final int OP_DAT = 3;
	public static final int OP_ACK = 4;
	public static final int OP_ERR = 5;
	
	public static InetSocketAddress socketAddress;
	public static int opcode;
	public static String params;
	
	// requestedFile variable moved to fields to allow access in ParseRQ method
	public StringBuffer requestedFile;
	
	public static void main(String[] args) {
		
		if (args.length > 0) 
		{
			System.err.printf("usage: java %s\n", TFTPServer.class.getCanonicalName());
			System.exit(1);
		}
		//Starting the server
		try 
		{
			TFTPServer server= new TFTPServer();
			server.start();
		}
		catch (SocketException e) 
			{e.printStackTrace();}
	}
	
	private void start() throws SocketException 
	{
		byte[] buf= new byte[BUFSIZE];
		
		// Create socket
		DatagramSocket socket= new DatagramSocket(null);
		
		// Create local bind point 
		SocketAddress localBindPoint= new InetSocketAddress(TFTPPORT);
		socket.bind(localBindPoint);

		System.out.printf("Listening at port %d for new requests\n", TFTPPORT);
		
		// Loop to handle client requests 
		while (true) 
		{        
			
			final InetSocketAddress clientAddress = receiveFrom(socket, buf);
			
			// If clientAddress is null, an error occurred in receiveFrom()
			if (clientAddress == null){
				continue;
			}

			final int reqtype = ParseRQ(buf);
			
			new Thread() 
			{
				public void run() 
				{
					try 
					{
						DatagramSocket sendSocket= new DatagramSocket(0);

						// Connect to client
						sendSocket.connect(clientAddress);						
						
						System.out.printf("%s request for %s from %s using port %d\n",
								(reqtype == OP_RRQ)?"Read":"Write",
										requestedFile, clientAddress.getHostName(), clientAddress.getPort());  
						
						// Read request
						if (reqtype == OP_RRQ) 
						{      
							requestedFile.insert(0, READDIR);
							
							/*try {
								File file = new File(requestedFile.toString());
								
								if(file.exists()){
									
									
									
								}
							} catch (Exception e) {
								e.printStackTrace();
							}*/
							
							HandleRQ(sendSocket, requestedFile.toString(), OP_RRQ);
						}
						// Write request
						else 
						{                       
							requestedFile.insert(0, WRITEDIR);
							
							/**try {
								File file = new File(requestedFile.toString());
								
								if(!file.exists()){
									
									file.createNewFile();
									
								}
							} catch (Exception e) {
								e.printStackTrace();
							}*/
							
							HandleRQ(sendSocket,requestedFile.toString(),OP_WRQ);  
						}
						sendSocket.close();
					} 
					catch (SocketException e) 
						{e.printStackTrace();}
				}
			}.start();
		}
	}
	
	/**
	 * Reads the first block of data, i.e., the request for an action (read or write).
	 * @param socket (socket to read from)
	 * @param buf (where to store the read data)
	 * @return socketAddress (the socket address of the client)
	 */
	private InetSocketAddress receiveFrom(DatagramSocket socket, byte[] buf) 
	{
		// Create datagram packet
		
		DatagramPacket dp = new DatagramPacket(buf, buf.length);
		// Receive packet
		
		try {
			System.out.println("Receiving..");
			socket.receive(dp);
			System.out.println(buf[7]);
			System.out.println("Received!");
			
			socketAddress = new InetSocketAddress(dp.getAddress(), dp.getPort());
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		// Get client address and port from the packet
		
		return socketAddress;
	}

	/**
	 * Parses the request in buf to retrieve the type of request and requestedFile
	 * 
	 * @param buf (received request)
	 * @param requestedFile (name of file to read/write)
	 * @return opcode (request type: RRQ or WRQ)
	 */
	private int ParseRQ(byte[] buf) 
	{
		// ByteBuffer used to get the first 2 bytes and convert it a short which represents the opcode.
		ByteBuffer wrap = ByteBuffer.wrap(buf);
		short opcode = wrap.getShort();
		
		System.out.println("Opcode: " + opcode);
		
		int nameLength = 0;
		
		// for loop to find the where in the byte array the name of the requested file ends.
		for(int i=2;i<buf.length;i++){
			
			// if element is == 0 then the name of the requested file has ended.
			if(buf[i] == 0) {
				
				nameLength = i;
				i=buf.length;
			}
			
		}
		
		// create a string which contains the name of the requested file using variable found in for loop to locate end.
		String fileName = new String(buf, 2 , nameLength-2);
		
		int transferTypeLength = 0;
		
		// for loop to find where in the bite array the transfer type ends.
		for(int i=nameLength+1;i<buf.length;i++){
			
			// if element is 0 we have found the length of the transfer type
			if(buf[i] == 0) {
				
				transferTypeLength = i;
				i=buf.length;
			}
			
		}
		
		// create string which contains name of the transfer type using variables found in for loop to locate
		String transferType = new String(buf, nameLength+1, transferTypeLength-nameLength-1);
		
		// set name of requested file to be used in the run method
		requestedFile = new StringBuffer(fileName);
		
		System.out.println(requestedFile);
		System.out.println(transferType);
		
		return opcode;
	}

	/**
	 * Handles RRQ and WRQ requests 
	 * 
	 * @param sendSocket (socket used to send/receive packets)
	 * @param requestedFile (name of file to read/write)
	 * @param opcode (RRQ or WRQ)
	 */
	private void HandleRQ(DatagramSocket sendSocket, String requestedFile, int opcode) 
	{		
		if(opcode == OP_RRQ)
		{
			// See "TFTP Formats" in TFTP specification for the DATA and ACK packet contents
			boolean result = send_DATA_receive_ACK(params);
		}
		else if (opcode == OP_WRQ) 
		{
			boolean result = receive_DATA_send_ACK(params);
		}
		else 
		{
			System.err.println("Invalid request. Sending an error packet.");
			// See "TFTP Formats" in TFTP specification for the ERROR packet contents
			send_ERR(params);
			return;
		}		
	}
	
	/**
	To be implemented
	*/
	private boolean send_DATA_receive_ACK(String params)
	{return true;}
	
	private boolean receive_DATA_send_ACK(String params)
	{return true;}
	
	private void send_ERR(String params)
	{}
	
}



