import java.io.*;
import java.net.*;
import java.util.*;

public class EchoServer {

	private static final byte[] SERVER_ADDRESS = {0,1,2,3};
	private static final byte[] SERVER_PORT = {4,5};
	private static final byte FLAGS = 6;
	private static final byte DATASIZE = 7;
	private static final byte SEQUENCE = 8;
	private static final byte CONNECTIONID = 9;
	private static final byte CRC = 10;
	private static final byte PADDING = 11;
	private static final byte MESSAGE = 12;

	private static DatagramSocket serverSocket;
	private static Map<Byte, ArrayList<Object>> table = new LinkedHashMap<Byte, ArrayList<Object>>();
	private static CRC8 crc = new CRC8();
	private static boolean trace = false;

	public static void main(String args[]) throws Exception
	{
		try {
			// Open a UDP datagram socket with a specified port number

			BufferedReader keyboardInput = new BufferedReader(new InputStreamReader(System.in));

			System.out.print("Please input port number: ");
			int port_number = Integer.parseInt(keyboardInput.readLine());
			serverSocket = new DatagramSocket(port_number);

			System.out.print("Would you like Trace on? yes or no: ");
			String input = keyboardInput.readLine();
			if (input.toLowerCase().charAt(0) == 'y')
				trace = true;

			URL whatip = new URL("http://api.externalip.net/ip/");
			keyboardInput = new BufferedReader(new InputStreamReader(whatip.openStream()));
			input = keyboardInput.readLine(); //you get the IP as a String
			System.out.println("IP Address and Port: " + input + ":" + port_number);

			// This will list everything the client has in their directory
			File file = new File("server/"); // current directory
			File[] files = file.listFiles();
			for (File fileTemp : files) {
				if (fileTemp.isDirectory()) {
					System.out.print("directory:");
				} else {
					System.out.print("     file:");
				}
				System.out.println(fileTemp.getName());
			}

			byte[] receiveData = new byte[4096];

			System.out.println("Server running...");
			while (true){
				if (trace)
					System.out.println("Table is empty: " + table.isEmpty());

				// Create a packet
				DatagramPacket receivePacket =
						new DatagramPacket(receiveData, receiveData.length);

				// Receive a packet
				serverSocket.receive(receivePacket);
				receiveData = receivePacket.getData();

				byte[] sendData = new byte[(receiveData[DATASIZE] & 0xFF) + 12];

				if (trace)
					System.out.println("Received packet from: " + receivePacket.getAddress());

				// Copy all the receiveData into the a new frame
				for(int i = 0; i < ((receiveData[DATASIZE] & 0xFF) + 12); i++)
					sendData[i] = receiveData[i];

				// CRC Check
				byte b = sendData[CRC];
				sendData[CRC] = 0;
				byte a = crc.checksum(sendData);
				if (!(a == b)) {
					if (trace)
						System.out.println("Failed CRC Check, dropped packet");
				} else {
					file = null;

					if (table.containsKey(receiveData[CONNECTIONID])){
						if (((receiveData[FLAGS] & 0x4) != 0)) {
							if (trace)
								System.out.println("Downloading a packet");

							ArrayList<Object> list = table.get(receiveData[CONNECTIONID]);
							download(receiveData, receivePacket, list);
						} else {
							// Upload statement, this will be run after the initial connection is established
							if (trace)
								System.out.println("Upload a packet");

							ArrayList<Object> list = table.get(receiveData[CONNECTIONID]);
							upload(receiveData, receivePacket, list);
						}
					} else
						// New Connection
						if (!table.containsKey(receiveData[CONNECTIONID])){

							if (((receiveData[FLAGS] & 0x4) == 0) & ((receiveData[FLAGS] & 0x8) == 0)) {
								// This should be upload to client

								if ((receiveData[FLAGS] & 0x2) != 0) {

									if (trace)
										System.out.println("New connection, upload, end three way handshake");

									file = new File("server/" + new String(receiveData, MESSAGE, (receiveData[DATASIZE] & 0xFF)));

									System.out.println("Upload Name: " + file.getName());

									ArrayList<Object> list = new ArrayList<Object>();

									list.add(0, file);
									list.add(1, (byte)(receiveData[SEQUENCE] + 1));

									table.put(receiveData[CONNECTIONID], list);

									upload(receiveData, receivePacket, list);
								} else {

									// Finish the second part of the three way handshake
									sendData[FLAGS] = (byte) (sendData[FLAGS] + 2);
									if (trace)
										System.out.println("Second part of upload handshake");

									//Adding the CRC check.
									sendData[CRC] = 0;
									sendData[CRC] = crc.checksum(sendData);

									// Create a packet
									DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, receivePacket.getAddress(), receivePacket.getPort());

									// Send a message			
									serverSocket.send(sendPacket);
								}

							} else 
								if (((receiveData[FLAGS] & 0x4) != 0) & ((receiveData[FLAGS] & 0x8) == 0)) { 
									// This should be download to client

									// Finish the second part of the three way handshake
									sendData[FLAGS] = (byte) (sendData[FLAGS] + 2);

									System.out.println("Second part of handshake, download");

									//Adding the CRC check.
									sendData[CRC] = 0;
									sendData[CRC] = crc.checksum(sendData);

									// Create a packet
									DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, receivePacket.getAddress(), receivePacket.getPort());

									// Send a message			
									serverSocket.send(sendPacket);

									// Makes sure the file has a unique name
									file = new File("server/" + new String(receiveData, MESSAGE, (receiveData[DATASIZE] & 0xFF)));
									for (int i = 0; i >= 0; i++){
										if (file.exists()) {
											file = new File("server/" + i + new String(receiveData, MESSAGE, (receiveData[DATASIZE] & 0xFF)));
										} else {
											break;
										}
									}

									// Puts the information into the table, finishes the three way
									System.out.println("Download Name: " + file.getName());

									ArrayList<Object> list = new ArrayList<Object>();

									list.add(0, file);
									list.add(1, (byte)(receiveData[SEQUENCE] + 1));

									table.put(receiveData[CONNECTIONID], list);
								}
						}
				}
			}

		} 
		catch (IOException ioEx) {
			ioEx.printStackTrace();
		} 
		finally {
			// Close the socket 
			serverSocket.close();
		}
	}

	public static void download(byte[] receiveData, DatagramPacket receivePacket, ArrayList<Object> list){

		try {

			byte sequence = (Byte) list.get(1);
			File file = (File) list.get(0);
			if ((receiveData[SEQUENCE] & 0xFF) == ((sequence & 0xFF))) {
				if (trace)
					System.out.println("Sequence Number: " + (receiveData[SEQUENCE] & 0xFF)
							+ "\nSequence Number Expected: " + ((sequence & 0xFF)));

				// Writes the data received to the file.
				PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(file, true)));
				pw.write(new String(receiveData, MESSAGE, (receiveData[DATASIZE] & 0xFF)));
				pw.close();

				if (trace) {
					// Display received message	
					String message = new String(receiveData, MESSAGE, (receiveData[DATASIZE] & 0xFF));
					System.out.println("The received message is: " + message);
				}

				list.set(1, ++sequence);

			} else {
				if (trace)
					System.out.println("Sequence Number: " + (receiveData[SEQUENCE] & 0xFF)
							+ "\nSequence Number Expected: " + ((sequence & 0xFF)));

			}

			// Create a buffer for sending
			byte[] sendData = new byte[12];

			sendData[SERVER_ADDRESS[0]] = receiveData[SERVER_ADDRESS[0]]; // Server Address
			sendData[SERVER_ADDRESS[1]] = receiveData[SERVER_ADDRESS[1]]; // Server Address
			sendData[SERVER_ADDRESS[2]] = receiveData[SERVER_ADDRESS[2]]; // Server Address
			sendData[SERVER_ADDRESS[3]] = receiveData[SERVER_ADDRESS[3]]; // Server Address
			sendData[SERVER_PORT[0]] = receiveData[SERVER_PORT[0]]; // Server Port
			sendData[SERVER_PORT[1]] = receiveData[SERVER_PORT[1]]; // Server Port
			sendData[FLAGS] = (byte) (receiveData[FLAGS]); // This changes the flag to an ACK
			sendData[DATASIZE] = 0; // Data Size of the message
			sendData[SEQUENCE] = receiveData[SEQUENCE]; // Sequence Number
			sendData[CONNECTIONID] = receiveData[CONNECTIONID]; // Connection ID
			sendData[PADDING] = receiveData[PADDING]; // Padding

			// In case the last flag is received, the download is ended here.
			if ((receiveData[FLAGS] & 0x8) != 0) {
				table.remove(receiveData[CONNECTIONID]);
				sendData[FLAGS] = (byte) (sendData[FLAGS] + 2);
				System.out.println("Ending download of: " + file.getName());
			}

			//Adding the CRC check.
			sendData[CRC] = 0;
			sendData[CRC] = crc.checksum(sendData);

			// Create a packet
			DatagramPacket sendPacket = 
					new DatagramPacket(sendData, sendData.length, receivePacket.getAddress(), receivePacket.getPort());

			// Send a message			
			serverSocket.send(sendPacket);

		} 
		catch (IOException ioEx) {
			ioEx.printStackTrace();
		} 
		finally {
			// Close the socket 
			//serverSocket.close();
		}

	}

	public static void upload(byte[] receiveData, DatagramPacket receivePacket, ArrayList<Object> list){

		File file = (File) list.get(0);

		try {
			// Sequence Number received
			if (trace)
				System.out.println("Sequence Number: " + (receiveData[SEQUENCE] & 0xFF));

			if ((receiveData[FLAGS] & 0x8) != 0) {
				System.out.println("Upload ending for: " + file.getName());
				table.remove(receiveData[CONNECTIONID]);
				return;
			}

			FileInputStream inputFile = new FileInputStream(file);
			byte[] sendData = new byte[256];

			// This will change the starting position based off sequence number
			long skip = 0;
			for (int i = 0; i < (receiveData[SEQUENCE] & 0xFF); i++)
				skip = skip + (sendData.length - MESSAGE);
			inputFile.skip(skip);

			//Checking the length of the file being sent
			if(inputFile.available() > sendData.length - MESSAGE){
				sendData[DATASIZE] = (byte)((sendData.length - MESSAGE) & 0xFF);
			} else {
				sendData = new byte[inputFile.available() + MESSAGE];
				sendData[FLAGS] = (byte) (sendData[FLAGS] + 8);
				sendData[DATASIZE] = (byte) (inputFile.available() & 0xFF);
			}

			sendData[SERVER_ADDRESS[0]] = receiveData[SERVER_ADDRESS[0]]; // Server Address
			sendData[SERVER_ADDRESS[1]] = receiveData[SERVER_ADDRESS[1]]; // Server Address
			sendData[SERVER_ADDRESS[2]] = receiveData[SERVER_ADDRESS[2]]; // Server Address
			sendData[SERVER_ADDRESS[3]] = receiveData[SERVER_ADDRESS[3]]; // Server Address
			sendData[SERVER_PORT[0]] = receiveData[SERVER_PORT[0]]; // Server Port
			sendData[SERVER_PORT[1]] = receiveData[SERVER_PORT[1]]; // Server Port
			sendData[SEQUENCE] = receiveData[SEQUENCE];
			sendData[CONNECTIONID] = receiveData[CONNECTIONID];
			sendData[PADDING] = receiveData[PADDING]; // Padding


			//Build the pay load
			inputFile.read(sendData, MESSAGE, (sendData[DATASIZE] & 0xFF));
			inputFile.close();

			// Making the size of the total message
			int length_of_message = sendData.length; 	

			//Adding the CRC check.
			sendData[CRC] = 0;
			sendData[CRC] = crc.checksum(sendData);

			// Create a packet
			DatagramPacket sendPacket = new DatagramPacket(sendData, length_of_message, receivePacket.getAddress(), receivePacket.getPort());

			// Send a message
			serverSocket.send(sendPacket);



		} catch (IOException ioEx) {
			ioEx.printStackTrace();
		} finally {
		}
	}
}