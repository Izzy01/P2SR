package sender_receiver;

import javax.swing.*;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Random;

/**
 * Receiver
 * public class Receiver extends JFrame implements ActionListener
 * The Receiver class represents a client written for the Sender of Go-back-n.
 * @author Ilnaz Daghighian
 */
public class Receiver extends JFrame implements ActionListener {
	
	private static final long serialVersionUID = 1L;
	private static final String UPLOAD_FOLDER = "C:\\temp\\";
	private JTextField windowSizeField = new JTextField(); 
	private JTextField corruptField = new JTextField(); 
	private JTextField receiverIPAddressField = new JTextField(); 
	private JTextField receiverPortField = new JTextField(); 
	private JButton runButton = new JButton("Run");
	private JTextArea displayArea = new JTextArea(); 
	
	private DatagramSocket receiveSocket;//socket to connect to server
	private DatagramPacket receivePacket;//packet to receive data 
	private DatagramPacket ack;
	private FileOutputStream fileOutput;
	
	@SuppressWarnings("unused")
	private int windowSize;
	private double disrupt;
	private String receiverIPAddress;
	private int port;
 
	private byte[] outputText; 
	private int seqnoSent; 
	private int acknoSent;//holds value of total packets being sent 

	private boolean drop; 
	private boolean corruptData; 
	
	private int sequenceToAck = 0; 
	private int waitingFor = 0; //sequence number of packet to receive and ACK
	
	
	public static void main( String[] args ) throws InterruptedException {
	      Receiver receiver = new Receiver(); 
	   	  receiver.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
	} 

	//set up GUI and DatagramSocket
	public Receiver() { 
      
	  super( "RECEIVER" );
	  setSize( 500, 800 ); 
	  setVisible( true ); 
	  
		/* LAYOUT */
		setLayout(new BorderLayout());
		
		/* TOP PANEL */
		JPanel topPanel = new JPanel(new GridLayout(2, 5));
		JLabel receiverIPAddressLabel = new JLabel("IP address: ");
		JLabel receiverPortLabel = new JLabel("Port: ");
		JLabel windowSizeLabel = new JLabel("W Size: ");
		JLabel dataCorrupLabel = new JLabel("% Disrupt:");
		
		topPanel.add(receiverIPAddressLabel);
		topPanel.add(receiverPortLabel);
		topPanel.add(windowSizeLabel);
		topPanel.add(dataCorrupLabel);
		topPanel.add(new JLabel());
		
		topPanel.add(receiverIPAddressField);
		topPanel.add(receiverPortField);
		topPanel.add(windowSizeField);
		topPanel.add(corruptField);
		topPanel.add(runButton);
		runButton.addActionListener(this);
		
		add(topPanel, BorderLayout.NORTH);

		/* BOTTOM PANEL */ 
		displayArea.setFont(new Font("Sans-Serif", Font.PLAIN, 16));
		displayArea.setLineWrap(true);
		displayArea.setWrapStyleWord(true);
		JScrollPane scrollPane = new JScrollPane(displayArea);
	    add(scrollPane, BorderLayout.CENTER);
      
   } //end constructor

   
   @Override
	public void actionPerformed(ActionEvent arg0) {
	   	//get user input and set values -then run 
		getData();	
		if(getData()){
			run();
		}
	}
   
   public void run() {
	   	//create DatagramSocket for sending and receiving packets
	      try {
	    	  InetAddress server = InetAddress.getByName(receiverIPAddress);
	    	  SocketAddress address = new InetSocketAddress(server, port );
	    	  receiveSocket = new DatagramSocket(address); 
	      }
	      catch ( SocketException | UnknownHostException socketException ) {
	         socketException.printStackTrace();
	         System.exit( 1 );
	      }
	      
	      byte[] data = new byte[512]; //set up receiving packet
	      
	      //keep receiving packets until you reach last packet -then ACK and close socket 
	      try {
	    	  while(true) {
	    		  
	    		  //receive packet
	    		  receivePacket = new DatagramPacket(data, data.length);
	    		  receiveSocket.receive(receivePacket); 

	    		  byte[] dataReceived = receivePacket.getData(); //get packet data 

	             //decode, and if packet received is not corrupt 
	             if(decodePacket(dataReceived)){ 
	            	 
            		 	if(seqnoSent == waitingFor && seqnoSent == acknoSent-1){
		            		displayMessage("\n[RECV]: " + "[" + seqnoSent + "] " + "[RECV]\n"); //last packet
		            		System.out.println("\n[RECV]: " + "[" + seqnoSent + "] " + "[RECV]\n");
		            		sequenceToAck++; 
		            		writeBytesToFile(outputText, UPLOAD_FOLDER + "output.txt");//write to file
		            		byte[] ackData = ackPacket(sequenceToAck); //create ACK packet for sender 
		             		ack = new DatagramPacket(ackData, ackData.length, receivePacket.getAddress(),
		             				 receivePacket.getPort());
		             		displayMessage("\n[ACK]: " + "[" + sequenceToAck + "] " + " [SENT]\n");
							System.out.println("\n[ACK]: " + "[" + sequenceToAck + "] " + " [SENT]\n");
							receiveSocket.send(ack);//send ACK packet to Sender	 
							displayMessage("\n[LAST PACKET RECEIVED]" + " [ " + System.currentTimeMillis() + " ]");
							System.out.println("\n[LAST PACKET RECEIVED]" + " [ " + System.currentTimeMillis() + " ]");
		            		break;
            		 	}
            		 	else if (seqnoSent == waitingFor) {
	             			waitingFor++;
	             			sequenceToAck = seqnoSent;
		            		displayMessage("\n[RECV]: " + "[" + seqnoSent + "] " + "[RECV]\n"); //packet is good
		            		System.out.println("\n[RECV]: " + "[" + seqnoSent + "] " + "[RECV]\n");
		            		writeBytesToFile(outputText, UPLOAD_FOLDER + "output.txt");//write to file
	             		} 
            		 	//all ACK's sent to sender are dropped and or corrupted
            		 	//receiver responds by sending an ACK for each frame that it receives
            		 	else if (seqnoSent < waitingFor) {
            		 		displayMessage("\n[DUPL]: " + "[" + seqnoSent + "] " + "[!SEQ]\n");//receivedPacket out of order 
			            	System.out.println("\n[DUPL]: " + "[" + seqnoSent + "] " + "[!SEQ]\n");
            		 		sequenceToAck = seqnoSent; 
            		 	}
			            else { 
			            	displayMessage("\n[RECV]: " + "[" + seqnoSent + "] " + "[!SEQ]\n");//receivedPacket out of order 
			            	System.out.println("\n[RECV]: " + "[" + seqnoSent + "] " + "[!SEQ]\n");
			            	sequenceToAck = waitingFor; 
			            }		         	
	             }
		         else { 
		            displayMessage("\n[RECV]: " + "[" + seqnoSent + "] " + "[CRPT]\n");//packet is corrupt
	            	System.out.println("\n[RECV]: " + "[" + seqnoSent + "] " + "[CRPT]\n"); 
	            	sequenceToAck = waitingFor; 
		         }	
	             //if packet sent is not out of sequence or corrupt send ACK 
	             if (sequenceToAck != waitingFor){
	            	 byte[] ackData = ackPacket(sequenceToAck); //create ACK packet for sender 
	          		 ack = new DatagramPacket(ackData, ackData.length, receivePacket.getAddress(),
	          				 receivePacket.getPort());
	       
	     			//send ACK packet with some probability of loss
	     			if (Math.random() > disrupt || sequenceToAck == 0){
	     				displayMessage("\n[ACK]: " + "[" + sequenceToAck + "] " + " [SENT]\n");
						System.out.println("\n[ACK]: " + "[" + sequenceToAck + "] " + " [SENT]\n");
						receiveSocket.send(ack);//send ACK packet to Sender
	     			}
	     			else {
	     				dropOrCorrupt();
	     				if (drop) {
		     				displayMessage("\n[ACK]: " + "[" + sequenceToAck + "] " + " [ DROP ]\n");
		    				System.out.println("\n[ACK]: " + "[" + sequenceToAck + "] " + " [ DROP ]\n");
		    				drop = false; 
	     				}
	     				if (corruptData) {
           				 byte[] corrupt = corruptAckPacket(sequenceToAck);
           				 displayMessage("\nACK:" + "[" + sequenceToAck + "]" + " [ERR]\n" );  
           				 System.out.println("\nACK:" + "[" + sequenceToAck + "]" + " [ERR]\n" );
           				 receiveSocket.send(new DatagramPacket(corrupt, corrupt.length, 
           						 receivePacket.getAddress(), receivePacket.getPort()));
           				 corruptData = false; 
	     				}
	     			}
	             }
     			
	      }// end while loop 
	     
	   }catch(Exception e){}
	      finally{
	    	  try{
	    		  fileOutput.close();
	    		//  receiveSocket.disconnect();
	    		//  receiveSocket.close();
	    	  }
	    	  catch(Exception e){}
	      } 
	 
   } //end run 
   	
   	//////////////PRIVATE HELPER METHODS////////////

	//method to capture text field data and convert accordingly 
   private boolean getData() {

	   	receiverIPAddress = receiverIPAddressField.getText();
		receiverIPAddress = (receiverIPAddress.equals("")) ? "localhost" : receiverIPAddress;//default
		
		String receiverPortStr = receiverPortField.getText();
		port = (receiverPortStr.equals("")) ? 5000 : Integer.parseInt(receiverPortStr);//set to 5000 for debugging

		String windowSizeStr = windowSizeField.getText();
		windowSize = (windowSizeStr.equals("")) ? 1 : 1;//window size fixed at one
		
		String corruptStr = corruptField.getText(); 
		disrupt = (corruptStr.equals("")) ? .3 : Double.parseDouble(corruptStr);//set to .3 for debugging  
			
		return true;	
	}
   
   	//method to decode packet
	private boolean decodePacket(byte[] data){ 
		
		byte[] checksumSent = Arrays.copyOfRange(data, 0, 2);
		byte[] ackNumberSent = Arrays.copyOfRange(data, 4, 8);
		byte[] sequenceNumberSent = Arrays.copyOfRange(data, 8, 12);
		byte[] dataSent = Arrays.copyOfRange(data, 12, 513);

		short checksumValueSent = bytesToShort(checksumSent);

		seqnoSent = bytesToInt(sequenceNumberSent);
		
		if (checksumValueSent != 0){
			return false;  //corrupt ACK 
		}
		acknoSent = bytesToInt(ackNumberSent);
		outputText = dataSent; 
		return true; 
	}

    //creates ackPacket byte[] for sending back to sender 
	private byte[] ackPacket(int sequenceNumToAck){
		
		byte[] checksum = shortToBytes((short)0);//good checksum = 0
		byte[] length = shortToBytes((short) 8);
		byte[] ackNumber = intToBytes(sequenceNumToAck);

		byte[] finalPacket = concat(checksum, length, ackNumber);

		return finalPacket;
		
	}
	
	//creates corrupt ACK packet byte[] for sending to sender 
	private byte[] corruptAckPacket(int sequenceNumToAck){
		
		byte[] checksum = shortToBytes((short) 1);//bad checksum = 1		
		byte[] length = shortToBytes((short) 8);
		byte[] ackNumber = intToBytes(sequenceNumToAck);

		byte[] finalPacket = concat(checksum, length, ackNumber);

		return finalPacket;
		
	}

	/////METHODS USING BUFFERS FOR CONVERTING PRIMATIVE VALUES TO BYTE[] AND VICE VERSA  -BIG ENDIAN/////

	//convert byte[] to short 
	private short bytesToShort(byte[] bytes) {
	     return ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN).getShort();
	}
	//convert short to byte[] 
	private byte[] shortToBytes(short value) {
	    return ByteBuffer.allocate(2).order(ByteOrder.BIG_ENDIAN).putShort(value).array();
	}
	//convert byte[] to int
	private int bytesToInt(byte [] bytes){
	    return ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN).getInt();
	}
	//convert int to byte[]
	private  byte[] intToBytes(int value){
	    return ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(value).array();
	}
   
	//method to concatenate byte[] 
	private byte[] concat(byte[]...arrays) {
	    //Determine the length of the result array
	    int totalLength = 0;
	    for (int i = 0; i < arrays.length; i++) {
	        totalLength += arrays[i].length;
	    }

	    //create the result array
	    byte[] result = new byte[totalLength];

	    //copy the source arrays into the result array
	    int currentIndex = 0;
	    for (int i = 0; i < arrays.length; i++) {
	        System.arraycopy(arrays[i], 0, result, currentIndex, arrays[i].length);
	        currentIndex += arrays[i].length;
	    }
	    return result;
	}
 	
    //write byte[] to .txt file 
	private void writeBytesToFile(byte[] bFile, String fileDest) {

         try {		 
        	fileOutput = new FileOutputStream(fileDest, true);
            fileOutput.write(bFile, 0, 500);
            fileOutput.flush();
 	     } catch (IOException e) {
 	            e.printStackTrace();
 	     }
	}
 	
    //method that will set drop or corrupt to true for ACK packets
 	private void dropOrCorrupt() {
 		
 		Random randomGenerator = new Random();
 		int random = randomGenerator.nextInt(2);
    	if(random == 0){
    		drop = true;
    	}
    	if(random == 1){
    		corruptData = true;   
    	}
	}
 
 	//method to update displayArea
    private void displayMessage(final String messageToDisplay) {
       SwingUtilities.invokeLater(
          new Runnable() {
             public void run() { 
                displayArea.append(messageToDisplay); //display message
             } 
          } 
       ); 
    } // end method displayMessage
			
}//end class
