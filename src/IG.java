import java.io.*;
import java.net.*;
import java.nio.*;
import java.util.*;
import java.util.concurrent.*;

public class IG {

	// Simple echo server.  Modified from some example on the Internet.

	private static final byte DATASIZE = 7;
	private static final byte SEQUENCE = 8;
	private static final byte CONNECTIONID = 9;
	private static final byte CRC = 10;
	private static final int SERVER_I = 0;
	private static final int SERVER_P = 1;
	private static final int CLIENT_I = 2;
	private static final int CLIENT_P = 3;

	private static byte connectionID = 1;
	static DatagramSocket serverSocket;
	private static Map<Byte, ArrayList<Object>> table = new LinkedHashMap<Byte, ArrayList<Object>>();
	private static int dataRate = 4000;
	private static CRC8 crc = new CRC8();
	private static boolean trace = false;
	private static double errorRate = 0;
	private static double dropRate = 0;
	private static Random random = new Random();

	public static void main(String args[]) throws Exception
	{
		try {
			// Open a UDP datagram socket with a specified port number

			BufferedReader keyboardInput = new BufferedReader(new InputStreamReader(System.in));

			System.out.print("Please input Internal Gate port number: ");
			int port_number = Integer.parseInt(keyboardInput.readLine());
			serverSocket = new DatagramSocket(port_number);

			System.out.print("Please input data rate speed: ");
			dataRate = Integer.parseInt(keyboardInput.readLine());
			
			System.out.print("Would you like Trace on? yes or no: ");
			String input = keyboardInput.readLine();
			if (input.toLowerCase().charAt(0) == 'y')
				trace = true;

			// Prompt the user on error rate.
			System.out.print("Error rate you'd like (0 - 100): ");
			input = keyboardInput.readLine();
			errorRate = (Double.parseDouble(input) / 100);

			// Prompt the user on drop rate.
			System.out.print("Drop rate you'd like (0 - 100): ");
			input = keyboardInput.readLine();
			dropRate = (Double.parseDouble(input) / 100);

			// Statistics
			if (trace) {
				File file = new File("statistics.txt");
				PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(file, true)));
				pw.write(new String("Expected number of retransmissions: " + (errorRate / (1 - errorRate))));
				pw.close();
				
				System.out.println("Expected number of retransmissions: " + (errorRate / (1 - errorRate)));
			}

			URL whatip = new URL("http://api.externalip.net/ip/");
			keyboardInput = new BufferedReader(new InputStreamReader(whatip.openStream()));
			input = keyboardInput.readLine(); //you get the IP as a String
			System.out.println("IP Address and Port: " + input + ":" + port_number);

			System.out.println("Internal Gateway running...");

			// Create a buffer for receiving
			byte[] receiveData = new byte[4096];

			// Run forever
			while (true) {
				// Ensures it never makes connectionID = 0
				if (connectionID == 0){
					connectionID++;
				}

				// Create a packet
				DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);

				// Receive a message			
				serverSocket.receive(receivePacket);

				if (trace)
					System.out.println("Packet Received");

				// Delay timer based on DataRecieved / DataRate
				long delay = (long) (((double) receivePacket.getLength() / dataRate) * 1000);
				if (trace)
					System.out.println("Delayed by: " + delay + " milliseconds.");

				// This causes a delay before doing the additional code
				TimeUnit.MILLISECONDS.sleep(delay);

				// New Connection setup
				if (receiveData[CONNECTIONID] == 0){
					if (trace)
						System.out.println("New connection detected");

					if (table.containsKey(connectionID))
						table.remove(connectionID);

					receiveData[CONNECTIONID] = connectionID++;

					// Corrects the CRC for the change in connectionID
					byte[] crcCorrection = new byte[(receiveData[DATASIZE] & 0xFF) + 12];

					// Copy all the receiveData into the a new frame
					for(int i = 0; i < ((receiveData[DATASIZE] & 0xFF) + 12); i++)
						crcCorrection[i] = receiveData[i];

					crcCorrection[CRC] = 0;
					receiveData[CRC] = crc.checksum(crcCorrection);

					// Prepare for sending an echo message
					InetAddress sourceAddress = receivePacket.getAddress();
					int source_port_number = receivePacket.getPort();


					// Get the server address
					byte[] serverAddress = Arrays.copyOfRange(receiveData, 0, 4);
					InetAddress addr = null;
					addr = InetAddress.getByAddress(serverAddress);
					InetAddress destination = addr;

					// Get the server port number
					byte[] tempPort = Arrays.copyOfRange(receiveData, 4, 6);
					ByteBuffer wrapped = ByteBuffer.wrap(tempPort);
					int dest_port_number = (int) wrapped.getShort();


					// Create the list and adding to the table
					ArrayList<Object> list = new ArrayList<Object>();

					list.add(SERVER_I, destination);
					list.add(SERVER_P, dest_port_number);
					list.add(CLIENT_I, sourceAddress);
					list.add(CLIENT_P, source_port_number);

					table.put(receiveData[CONNECTIONID], list);
				}

				ArrayList<Object> list = table.get(receiveData[CONNECTIONID]);

				InetAddress serverAddress = (InetAddress) list.get(SERVER_I);
				int serverPort = (Integer) list.get(SERVER_P);
				InetAddress clientAddress = (InetAddress) list.get(CLIENT_I);
				int clientPort = (Integer) list.get(CLIENT_P);

				// Display received message and client address
				if (trace){
					//System.out.println("The received message is: " + message);
					//System.out.println("The client address is: " + clientAddress);
					//System.out.println("The client port number is: " + clientPort);
					//System.out.println("The server address is: " + serverAddress);
					//System.out.println("The server port number is: " + serverPort);
				}

				InetAddress destination;
				int destinationPort;

				if (receivePacket.getAddress().equals(clientAddress) & (receivePacket.getPort() == clientPort)){

					if (trace)
						System.out.println("Recieved from Client, sending to Server");

					destination = serverAddress;
					destinationPort = serverPort;
				} else {

					if (trace)
						System.out.println("Recieved from Server, sending to Client");

					destination = clientAddress;
					destinationPort = clientPort;
				}

				// Create a buffer for sending
				byte[] sendData = new byte[(receiveData[DATASIZE] & 0xFF) + 12];
				// Copy all the receiveData into the a new frame
				for(int i = 0; i < ((receiveData[DATASIZE] & 0xFF) + 12); i++)
					sendData[i] = receiveData[i];

				// Random
				if ((random.nextDouble() - errorRate) > 0) {

					// Create a packet
					DatagramPacket sendPacket = 
							new DatagramPacket(sendData, sendData.length, destination, destinationPort);

					// Send a message			
					serverSocket.send(sendPacket);

				} else {
					if (random.nextDouble() - dropRate > 0){
						// Error generation
						sendData[3] = 10;
						if (trace)
							System.out.println("Frame " + (sendData[SEQUENCE] & 0xFF) + " transmitted, Error");

						// Create a packet
						DatagramPacket sendPacket = 
								new DatagramPacket(sendData, sendData.length, destination, destinationPort);

						// Send a message			
						serverSocket.send(sendPacket);
					} else {
						// Dropped Packet
						if (trace)
							System.out.println("Frame " + (sendData[SEQUENCE] & 0xFF) + " dropped");
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
}
