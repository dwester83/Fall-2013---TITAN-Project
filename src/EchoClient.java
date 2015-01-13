import java.io.*;
import java.net.*;
import java.nio.*;

public class EchoClient {

	// Header laid out in order
	private static final byte[] SERVER_ADDRESS = {0,1,2,3};
	private static final byte[] SERVER_PORT = {4,5};
	private static final byte FLAGS = 6;
	private static final byte DATASIZE = 7;
	private static final byte SEQUENCE = 8;
	private static final byte CONNECTIONID = 9;
	private static final byte CRC = 10;
	private static final byte PADDING = 11;
	private static final byte MESSAGE = 12;

	private static DatagramSocket clientSocket;
	private static CRC8 crc = new CRC8();
	private static int dataSize = 256;
	private static int rto = (dataSize * 2);
	private static boolean trace = false;

	// This is for logging things
	private static long startTime = 0;
	private static long timeRequired = 0;
	private static int datagramGeneratedCount = 0;
	private static int totalTransmitted = 0;
	private static int packet = 0;
	private static int maxRetransmissions = 0;

	public static void main (String args[]) throws Exception {

		try {
			BufferedReader keyboardInput = new BufferedReader(new InputStreamReader(System.in));

			System.out.println("Upload (u) or Download (d)");
			String input = keyboardInput.readLine().toLowerCase();
			char load = input.charAt(0);

			// This will list everything the client has in their directory
			File file = new File("client/"); // current directory
			File[] files = file.listFiles();
			for (File fileTemp : files) {
				if (fileTemp.isDirectory()) {
					System.out.print("directory:");
				} else {
					System.out.print("     file:");
				}
				System.out.println(fileTemp.getName());
			}

			// Open a UDP datagram socket
			clientSocket = new DatagramSocket();

			// Determine Internal Gateway IP
			System.out.print("Please input Internal Gateway IP in #.#.#.# format: ");
			input = keyboardInput.readLine();
			String[] parts = input.split("\\.");
			byte [] igAddress = new byte[] {(byte)Integer.parseInt(parts[0]),(byte)Integer.parseInt(parts[1]),
					(byte)Integer.parseInt(parts[2]),(byte)Integer.parseInt(parts[3])};
			//byte [] igAddress = new byte[] {(byte)127,(byte)0,(byte)0,(byte)1};

			// Places the IP into a destination
			InetAddress addr = null;
			try {
				addr = InetAddress.getByAddress(igAddress);
			} catch (UnknownHostException impossible) {
				System.out.println("Unable to determine the host by address!");
			}
			InetAddress destination = addr;

			// Determine IG port number
			System.out.print("Please input Internal Gateway port number: ");
			int destinationPort = Integer.parseInt(keyboardInput.readLine());
			//int destinationPort = 3000;

			// Determine Server IP
			System.out.print("Please input server IP in #.#.#.# format: ");
			input = keyboardInput.readLine();
			parts = input.split("\\.");
			byte [] serverAddress = new byte[] {(byte)Integer.parseInt(parts[0]),(byte)Integer.parseInt(parts[1]),
					(byte)Integer.parseInt(parts[2]),(byte)Integer.parseInt(parts[3])};
			//byte [] serverAddress = new byte[] {(byte)127,(byte)0,(byte)0,(byte)1};

			// Determine server port number
			System.out.print("Please input server port number: ");
			short serverPort = Short.parseShort(keyboardInput.readLine());
			//short serverPort = 2000;

			// Converts a short into a byte array
			ByteBuffer buffer = ByteBuffer.allocate(serverPort);
			buffer.putShort(serverPort);
			byte[] serverPortArray = buffer.array();

			// File to be download
			System.out.print("File name to download or upload (must be full name): ");
			input = keyboardInput.readLine();
			file = new File("client/" + input);
			//file = new File("client/" + "longest.txt");

			// Create the send data and size
			if (file.getName().length() > dataSize)
				throw new FileNotFoundException("The file name was too large");
			byte[] sendData = new byte[(int) (file.getName().length() + 12)];

			System.out.print("Would you like Trace on? yes or no: ");
			input = keyboardInput.readLine();
			if (input.toLowerCase().charAt(0) == 'y')
				trace = true;

			// Building the frame
			sendData[SERVER_ADDRESS[0]] = serverAddress[0]; // Server Address
			sendData[SERVER_ADDRESS[1]] = serverAddress[1]; // Server Address
			sendData[SERVER_ADDRESS[2]] = serverAddress[2]; // Server Address
			sendData[SERVER_ADDRESS[3]] = serverAddress[3]; // Server Address
			sendData[SERVER_PORT[0]] = serverPortArray[0]; // Server Port
			sendData[SERVER_PORT[1]] = serverPortArray[1]; // Server Port
			sendData[FLAGS] = (byte) (sendData[FLAGS] + 1); // This is connect flag
			sendData[DATASIZE] = (byte) (file.getName().length() & 0xFF); // setting the data size

			// Adding the file name to the outgoing message
			for (int i = 0; i < file.getName().length(); i++)
				sendData[i + MESSAGE] = file.getName().getBytes()[i];

			// Sets the timeout timer to the client socket
			clientSocket.setSoTimeout(rto);

			// Start timer here
			startTime = System.nanoTime();

			// Three way hand shake, based on upload or download
			if (load == 'd') { // Download case

				//Adding the CRC check.
				sendData[CRC] = 0;
				sendData[CRC] = crc.checksum(sendData);

				// Datagram Generated
				datagramGeneratedCount++;
				packet = 0;
				DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, destination, destinationPort);

				// Increase total Transmissions
				totalTransmitted++;
				packet++;
				if (packet > maxRetransmissions)
					maxRetransmissions = packet;

				// Send a message			
				clientSocket.send(sendPacket);


				if (trace) {
					System.out.println("Sent download request");
				}

				// Create a packet
				byte[] receiveData = sendData.clone();
				DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);

				do {
					do {
						try {			
							// Receive a message
							clientSocket.receive(receivePacket);
							break;
						} catch (SocketTimeoutException e) {
							if (trace)
								System.out.println("Timeout, retransmitting");

							// Increase total Transmissions
							totalTransmitted++;
							packet++;
							if (packet > maxRetransmissions)
								maxRetransmissions = packet;

							// Send a message			
							clientSocket.send(sendPacket);

							if (trace) {
								System.out.println("Sent download request");
							}

							receivePacket = new DatagramPacket(receiveData, receiveData.length);
						}
					} while (true);

					if (trace) {
						System.out.println("Receive download ACK, checking CRC");
					}

					// CRC Check
					byte b = receiveData[CRC];
					receiveData[CRC] = 0;
					byte a = crc.checksum(receiveData);
					if (!(a == b)) {
						if (trace)
							System.out.println("CRC Failed, dropped packet and resending previous ACK...");
					} else {
						if (trace)
							System.out.println("CRC Passed, Second part of handshake");

						if ((receiveData[FLAGS] & 0x2) != 0) { // ACK 
							// Grabbing the connection ID and setting flags with an ACK
							sendData[CONNECTIONID] = receiveData[CONNECTIONID];
							sendData[FLAGS] = receiveData[FLAGS];

							//Adding the CRC check.
							sendData[CRC] = 0;
							sendData[CRC] = crc.checksum(sendData);

							// Datagram Generated
							datagramGeneratedCount++;
							packet = 0;
							sendPacket = new DatagramPacket(sendData, sendData.length, destination, destinationPort);

							// Increase total Transmissions
							totalTransmitted++;
							packet++;
							if (packet > maxRetransmissions)
								maxRetransmissions = packet;

							// Send a message			
							clientSocket.send(sendPacket);

							if (trace) {
								System.out.println("Third part of three way handshake sent");
							}

							//File
							// This will cause files to not keep being overwritten
							for (int i = 0; i >= 0; i++){
								if (file.exists()) {
									file = new File("client/" + i + new String(receiveData, MESSAGE, (receiveData[DATASIZE] & 0xFF)));
								} else {
									break;
								}
							}

							// Download statement, this will be run after the initial connection is established
							download(sendData, sendPacket, file);
							break;
						}
					}
				} while (true);

			} else { // Upload case
				sendData[FLAGS] = (byte) (sendData[FLAGS] + 4); // This is upload flag

				//Adding the CRC check.
				sendData[CRC] = 0;
				sendData[CRC] = crc.checksum(sendData);

				// Datagram Generated
				datagramGeneratedCount++;
				packet = 0;
				DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, destination, destinationPort);

				// Increase total Transmissions
				totalTransmitted++;
				packet++;
				if (packet > maxRetransmissions)
					maxRetransmissions = packet;

				// Send a message			
				clientSocket.send(sendPacket);

				if (trace) {
					System.out.println("Sent upload request");
				}

				// Create a packet
				byte[] receiveData = sendData.clone();
				DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);

				do {
					do {
						try {			
							// Receive a message
							clientSocket.receive(receivePacket);
							break;
						} catch (SocketTimeoutException e) {
							if (trace)
								System.out.println("Timeout, retransmitting");

							// Increase total Transmissions
							totalTransmitted++;
							packet++;
							if (packet > maxRetransmissions)
								maxRetransmissions = packet;

							// Send a message			
							clientSocket.send(sendPacket);

							receivePacket = new DatagramPacket(receiveData, receiveData.length);
						}
					}while (true);

					receiveData = receivePacket.getData();

					if (trace)
						System.out.println("Receive upload ACK");

					// CRC Check
					byte b = receiveData[CRC];
					receiveData[CRC] = 0;
					byte a = crc.checksum(receiveData);
					if (!(a == b)) {
						if (trace)
							System.out.println("CRC Failed, dropped packet and resending previous ACK...");
					} else {
						break;
					}
				} while (true);

				sendData = new byte[dataSize];

				// Copying over the header info for ACK
				for (int i = 0; i < 12; i++)
					sendData[i] = receiveData[i];

				System.out.println("Starting upload: " + file.getName());

				FileInputStream inputFile = new FileInputStream(file);
				upload(sendData, sendPacket, inputFile);
				inputFile.close();
			}
		}
		catch (FileNotFoundException e) {
			System.out.println(e.getMessage());
		} catch (IOException ioEx) {
			ioEx.printStackTrace();
		} finally {
			// Close the socket and file stream if used
			clientSocket.close();

			// Ending statistics
			if (trace) {
				File file = new File("statistics.txt");
				PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(file, true)));
				pw.write(new String(
						"\nTotal time taken: " + timeRequired + " nanoseconds." 
						+ "\nTotal datagrams generated: " + datagramGeneratedCount
						+ "\nTotal datagrams transmitted: " + totalTransmitted
						+ "\nTotal retransmissions: " + (totalTransmitted - datagramGeneratedCount)
						+ "\nMax retransmissions of a single packet: " + maxRetransmissions
						+ "\nPercent retransmission: " + ((((double)totalTransmitted - (double)datagramGeneratedCount) / (double)totalTransmitted)) * 100)
				+ "\n\n");
				pw.close();

				System.out.println(
						"\nTotal time taken: " + timeRequired + " nanoseconds." 
						+ "\nTotal datagrams generated: " + datagramGeneratedCount
						+ "\nTotal datagrams transmitted: " + totalTransmitted
						+ "\nTotal retransmissions: " + (totalTransmitted - datagramGeneratedCount)
						+ "\nMax retransmissions of a single packet: " + maxRetransmissions
						+ "\nPercent retransmission: " + ((((double)totalTransmitted - (double)datagramGeneratedCount) / (double)totalTransmitted)) * 100);
			}
		}
	}

	public static void download(byte[] sendData, DatagramPacket sendPacket, File file) throws IOException{


		System.out.println("Client Download starts...");

		byte sequence = 0;
		// Create a buffer for receiving
		byte[] receiveData = new byte[dataSize];
		DatagramPacket receivePacket;
		// Run until last is reached
		do {
			do { 
				do {
					// Create a packet
					receivePacket = new DatagramPacket(receiveData, receiveData.length);

					if (trace)
						System.out.println("Waiting on ACK");

					try {
						// Receive a message			
						clientSocket.receive(receivePacket);
						receiveData = receivePacket.getData();
						break;
					} catch (SocketTimeoutException e) {
						if (trace)
							System.out.println("Timeout, retransmitting");

						// Increase total Transmissions
						totalTransmitted++;
						packet++;
						if (packet > maxRetransmissions)
							maxRetransmissions = packet;

						clientSocket.send(sendPacket);
					}
				} while (true);

				if (trace)
					System.out.println("Received Sequence: " + (receiveData[DATASIZE] & 0xFF));

				// Corrects the CRC for the change in connectionID
				byte[] crcCorrection = new byte[(receiveData[DATASIZE] & 0xFF) + 12];

				// Copy all the receiveData into the a new frame
				for(int i = 0; i < ((receiveData[DATASIZE] & 0xFF) + 12); i++)
					crcCorrection[i] = receiveData[i];

				// CRC Check
				byte b = crcCorrection[CRC];
				crcCorrection[CRC] = 0;
				byte a = crc.checksum(crcCorrection);
				if (!(a == b)) {
					if (trace)
						System.out.println("Recieved for download, CRC Failed");
				} else {

					if (receiveData[SEQUENCE] == sequence) {
						// Writes the data received to the file.
						PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(file, true)));
						pw.write(new String(receiveData, MESSAGE, (receiveData[DATASIZE] & 0xFF)));
						pw.close();

						if (trace){
							// Display received message	
							String message = new String(receiveData, MESSAGE, (receiveData[DATASIZE] & 0xFF));
							System.out.println("The received message is: " + message);
						}

						sequence++;
					}

					sendData = new byte[12];

					sendData[SERVER_ADDRESS[0]] = receiveData[SERVER_ADDRESS[0]]; // Server Address
					sendData[SERVER_ADDRESS[1]] = receiveData[SERVER_ADDRESS[1]]; // Server Address
					sendData[SERVER_ADDRESS[2]] = receiveData[SERVER_ADDRESS[2]]; // Server Address
					sendData[SERVER_ADDRESS[3]] = receiveData[SERVER_ADDRESS[3]]; // Server Address
					sendData[SERVER_PORT[0]] = receiveData[SERVER_PORT[0]]; // Server Port
					sendData[SERVER_PORT[1]] = receiveData[SERVER_PORT[1]]; // Server Port
					sendData[FLAGS] = (byte) (receiveData[FLAGS] + 3); // This changes the flag to an ACK
					sendData[DATASIZE] = 0; // Data Size of the message
					sendData[SEQUENCE] = ++receiveData[SEQUENCE]; // Sequence Number
					sendData[CONNECTIONID] = receiveData[CONNECTIONID]; // Connection ID
					sendData[PADDING] = receiveData[PADDING]; // Padding

					//Adding the CRC check.
					sendData[CRC] = 0;
					sendData[CRC] = crc.checksum(sendData);

					// Datagram Generated
					datagramGeneratedCount++;
					packet = 0;
					// Create a packet
					sendPacket = new DatagramPacket(sendData, sendData.length,  sendPacket.getAddress(), sendPacket.getPort());

					// Increase total Transmissions
					totalTransmitted++;
					packet++;
					if (packet > maxRetransmissions)
						maxRetransmissions = packet;

					clientSocket.send(sendPacket);
					break;
				}
			} while (true);
		} while ((receiveData[FLAGS] & 0x8) == 0);
		System.out.println("Download Finished: " + file.getName());

		// End timer here
		timeRequired = System.nanoTime() - startTime;
	}

	public static void upload(byte[] sendData, DatagramPacket sendPacket, FileInputStream inputFile) throws IOException{

		try {

			int count = 0;
			byte sequence = 0;

			do {
				//Checking the length of the file being sent
				if(inputFile.available() > sendData.length - MESSAGE){
					sendData[DATASIZE] = (byte)((sendData.length - MESSAGE) & 0xFF);
				} else {
					sendData = new byte[inputFile.available() + MESSAGE];
					sendData[SERVER_ADDRESS[0]] = sendPacket.getData()[SERVER_ADDRESS[0]]; // Server Address
					sendData[SERVER_ADDRESS[1]] = sendPacket.getData()[SERVER_ADDRESS[1]]; // Server Address
					sendData[SERVER_ADDRESS[2]] = sendPacket.getData()[SERVER_ADDRESS[2]]; // Server Address
					sendData[SERVER_ADDRESS[3]] = sendPacket.getData()[SERVER_ADDRESS[3]]; // Server Address
					sendData[SERVER_PORT[0]] = sendPacket.getData()[SERVER_PORT[0]]; // Server Port
					sendData[SERVER_PORT[1]] = sendPacket.getData()[SERVER_PORT[1]]; // Server Port
					sendData[SEQUENCE] = sendPacket.getData()[SEQUENCE];
					sendData[CONNECTIONID] = sendPacket.getData()[CONNECTIONID];
					sendData[PADDING] = sendPacket.getData()[PADDING]; // Padding

					sendData[FLAGS] = (byte) (sendData[FLAGS] + 13);
					sendData[DATASIZE] = (byte) (inputFile.available() & 0xFF);
				}

				//Build the pay load
				inputFile.read(sendData, MESSAGE, (sendData[DATASIZE] & 0xFF));

				// Sets the sequence number
				sendData[SEQUENCE]++;
				sequence++;

				//Adding the CRC check.
				sendData[CRC] = 0;
				sendData[CRC] = crc.checksum(sendData);

				// Prepare for receiving
				byte[] receiveData = new byte[12];
				count = 0;

				// Datagram Generated
				datagramGeneratedCount++;
				packet = 0;
				// Create a packet
				sendPacket = new DatagramPacket(sendData, sendData.length, sendPacket.getAddress(), sendPacket.getPort());

				do {
					//Starts the loop in case of errors
					do {
						// Increase total Transmissions
						totalTransmitted++;
						packet++;
						if (packet > maxRetransmissions)
							maxRetransmissions = packet;

						// Send a message
						clientSocket.send(sendPacket);
						if (trace)
							System.out.println("Sent, Sequence Number: " + (sendData[SEQUENCE] & 0xFF));

						// Create a packet
						DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);

						try {
							clientSocket.receive(receivePacket);
							receiveData = receivePacket.getData();
							break;
						} catch (SocketTimeoutException e) {
							if (trace)
								System.out.println("Timeout, retransmitting");
							count++;
							if ((count > 5) & (sendData[FLAGS] & 0x8) != 0) {
								break;
							}
						}
					} while (true);

					// CRC Check
					byte b = receiveData[CRC];
					receiveData[CRC] = 0;
					byte a = crc.checksum(receiveData);
					if (!(a == b)) {
						// The CRC check failed
						if (trace)
							System.out.println("Received, Sequence Number: " + (sendData[SEQUENCE] & 0xFF) + " failed");
						if ((count > 5) & (sendData[FLAGS] & 0x8) != 0) {
							break;
						}
					} else {
						// CRC passed, Checking for the ACK, if not will timeout and retransmit
						if (((receiveData[FLAGS] & 0x2) != 0) & (receiveData[SEQUENCE] & 0xFF) == (sequence & 0xFF)) {
							if (trace)
								System.out.println("Received, Sequence Number: " + (sendData[SEQUENCE] & 0xFF) + " passed");
							break;
						}
					}
				} while (true);
			} while ((sendData[FLAGS] & 0x8) == 0); // Last flag
			System.out.println("Ending upload");

			// End timer here
			timeRequired = System.nanoTime() - startTime;

		} catch (IOException ioEx) {
			ioEx.printStackTrace();
		} finally {
			inputFile.close();
		}
	}
}
