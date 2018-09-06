package sender_receiver;

import javax.swing.*;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.nio.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Sender
 * public class Sender extends JFrame implements ActionListener
 * The Sender class represents a server implementing the Go-Back-N protocol.
 * @author Ilnaz Daghighian
 */
public class Sender extends JFrame implements ActionListener{
	
	private static final long serialVersionUID = 1L;
	private JTextField filePathField = new JTextField(); 
	private JTextField sizeOfPacketField = new JTextField(); 
	private JTextField timeoutField = new JTextField(); 
	private JTextField windowSizeField = new JTextField(); 
	private JTextField corruptField = new JTextField(); 
	private JTextField receiverIPAddressField = new JTextField(); 
	private JTextField receiverPortField = new JTextField(); 
	private JButton sendButton = new JButton("Send");
	private JTextArea displayArea = new JTextArea(); 

	private String filePath; 
	private int sizeOfPacket;
	private int timeout;
	private int windowSize;
	private double disrupt;
	private String receiverIPAddress;
	private int receiverPort;
	
	private DatagramSocket sendSocket; //socket to connect to server
	private DatagramPacket sendPacket; //packet being sent to receiver
	private DatagramPacket ackPacket; //packet being prepared to receive ACK
	private DatagramPacket delayedPacket; //delay packet 
	
	private List<byte[]> packetsWithProtocol; 
	private int numOfPackets;//last packet sequence number
	
	private int acknoSent;//ACK number sent from receiver 
	private boolean drop; 
	private boolean corruptData;
	private boolean delay;
	private long msTimeout;//delay in milliseconds
	private volatile boolean delayOn = false; 

	private int lastSent = 0;//sequence number of the last packet sent
	private int waitingForAck = 0;//sequence number of the last ACKed packet
	private List<byte[]> sent = new ArrayList<byte[]>();//list of all the packets sent

	
	//main method 
	public static void main( String[] args ) {
	     Sender sender = new Sender(); 
	     sender.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
	} 
	
	//set up GUI 
	public Sender() {
		    
	   super( "SENDER" );
	   setSize( 600, 800 ); 
	   setVisible( true ); 
      
      	/* LAYOUT */
		setLayout(new BorderLayout());
		
		/* TOP PANEL */
		JPanel topPanel = new JPanel(new GridLayout(2, 8));
		JLabel addressOfFileLabel = new JLabel("File path:");
		JLabel sizeOfPacketLabel = new JLabel("P Size:");
		JLabel timeOutLabel = new JLabel("Timeout:");
		JLabel windowSizeLabel = new JLabel("W Size:");
		JLabel dataCorrupLabel = new JLabel("% Disrupt:");
		JLabel receiverIPAddressLabel = new JLabel("Rec IPaddr:");
		JLabel receiverPortLabel = new JLabel("Rec Port:");
		
		topPanel.add(addressOfFileLabel);
		topPanel.add(sizeOfPacketLabel);
		topPanel.add(timeOutLabel);
		topPanel.add(windowSizeLabel);
		topPanel.add(dataCorrupLabel);
		topPanel.add(receiverIPAddressLabel);
		topPanel.add(receiverPortLabel);
		topPanel.add(new JLabel());

		topPanel.add(filePathField);
		topPanel.add(sizeOfPacketField);
		topPanel.add(timeoutField);
		topPanel.add(windowSizeField);
		topPanel.add(corruptField);
		topPanel.add(receiverIPAddressField);
		topPanel.add(receiverPortField);
		topPanel.add(sendButton);
		sendButton.addActionListener(this); 
		add(topPanel, BorderLayout.NORTH);

		/* BOTTOM PANEL */ 
		displayArea.setFont(new Font("Sans-Serif", Font.PLAIN, 16));
		displayArea.setLineWrap(true);
		displayArea.setWrapStyleWord(true);
		JScrollPane scrollPane = new JScrollPane(displayArea);
	    add(scrollPane, BorderLayout.CENTER);

	  }//end constructor
	   
	   @Override
	   public void actionPerformed(ActionEvent event) {
   			//get user input and set values- then run 
   			getData();	
   			if (getData()) {
   				run();
   			}
       } 
	   
	   public void run() {
		   try{
			   	////creating packets from user input////          
	      		byte[] bFile = readBytesFromFile(filePath);// convert file to byte[] 
	      		
	      		//splitting byte[] bFile into given size of packet 
	      		List<byte[]> listOfByteArrays = splitBytesArray(bFile, (sizeOfPacket-12));
	      		//adding protocol bytes 
	  			packetsWithProtocol = byteArrayWithProtocol(listOfByteArrays);
	  			numOfPackets = packetsWithProtocol.size(); //total number of packets 
	  			
	  			displayMessage("NUMBER OF PACKETS: " + numOfPackets + "\n");
	  			System.out.println("NUMBER OF PACKETS: " + numOfPackets + "\n");

	  		  //create DatagramSocket for sending and receiving packets
		      try {
		         sendSocket = new DatagramSocket();
		         sendSocket.connect(InetAddress.getByName(receiverIPAddress), receiverPort);
		      } 
		      catch ( SocketException | UnknownHostException  socketException ) {
		         System.exit( 1 );
		      } 
 			
  	  			int startByteOffset = 0;
  	  			int endByteOffset = -1;
  	  			String message = "";
  	  			
  	  			
  	  			/// while there are packets to send do the following ///  	  			
  	  			while(true) {
	
  	  				//sending loop -if there are still packets get them ready to send -- if bad ACK/no ACK re-send same packet 
  	  				while(lastSent - waitingForAck < windowSize && lastSent < numOfPackets){

  	      				byte[] data = packetsWithProtocol.get(lastSent);//get packet to send
  	      				sendPacket = new DatagramPacket(data, data.length, InetAddress.getByName(receiverIPAddress), 
  	      									receiverPort);//create packet 
  	      				sent.add(data); //add packet to the sent list
  	      				
  	      				//data needed to display information 
  	      				startByteOffset = (endByteOffset + 1);
  	      				endByteOffset = (startByteOffset + data.length);
  	      				message = "["+ startByteOffset + " : " + endByteOffset + "]";
  	      				
  	      			//send with some probability of loss
  	  				if(Math.random() > disrupt || lastSent == 0 || lastSent == (numOfPackets - 1)){
  	  				displayMessage("\n[SENDing]: " + "[" + lastSent + "] " + message + 
    							" [" + System.currentTimeMillis() + "]" + " [SENT]\n");
    					System.out.println("\n[SENDing]: " + "[" + lastSent + "] " +  message + 
    							" [" + System.currentTimeMillis() + "]" + " [SENT]\n");
    					sendSocket.send(sendPacket); 
  	  				}
  	  				else {
  	  					dropCorruptOrDelay();
  	  					
  	  					if(drop == false && corruptData == false && delay == false){
		  					displayMessage("\n[SENDing]: " + "[" + lastSent + "] " + message + 
									" [" + System.currentTimeMillis() + "]" + " [SENT]\n");
							System.out.println("\n[SENDing]: " + "[" + lastSent + "] " +  message + 
									" [" + System.currentTimeMillis() + "]" + " [SENT]\n");
							sendSocket.send(sendPacket); 
	  					}
  	  					if (drop) {
	    					displayMessage( "\n[SENDing]: " + "[ " + lastSent + " ] " +  message +
								" [ " + System.currentTimeMillis() + " ]" + " [ DROP ]\n");
	    					System.out.println("\n[SENDing] : " + "[ " + lastSent + " ] " + message + 
								" [ " + System.currentTimeMillis() + " ]" + " [ DROP ]\n"); 
	    					drop = false; 
  	  					}
  	  					if(corruptData) {	
  	  					byte[] corrupt = corruptPacket(lastSent);
	      					DatagramPacket corruptPacket = new DatagramPacket(corrupt, corrupt.length, 
	      							InetAddress.getByName(receiverIPAddress), receiverPort);
	      					sendSocket.send(corruptPacket);
	      					displayMessage("\n[SENDing]: " + "[ " + lastSent + " ] " + " [ CORRUPTDATAPACKET ]" + 
	      							" [" + System.currentTimeMillis() + "]" + " [ ERRR ]\n");
	      					System.out.println("\n[SENDing]: " + "[ " + lastSent + " ] " + " [ CORRUPTDATAPACKET ]" + 
	      							" [" + System.currentTimeMillis() + "]" + " [ ERRR ]\n");
	      					corruptData = false;
	      				}
  	  					if(delay) {
	      					displayMessage( "\n[SENDing]: " + "[" + lastSent + "] " + "[" + message + "] " + "[" + System.currentTimeMillis() + "]" + "[DLYD]\n");
	      					delayedPacket = sendPacket;//create the delayed packet
	      					msTimeout = (timeout+50);
	      					delayOn = true;
	      					//initiate a thread to delay the sending of packet
	      			        Thread t =  new Thread(new Runnable() {
	      			                public void run() {
	      			                   try {
										waitUntilZero(msTimeout);
	      			                   } catch (InterruptedException | IOException e) {
										e.printStackTrace();
	      			                   } 
	      			                }
	      			        });
	      			        t.start();//start thread 
	      					delay = false; 
	      				}
  	  				}

  	  				//increase the last sent
  	  				lastSent++;
  	  				
  	  			}//end of sending while
  	  				
  	  				/////wait for expected ACK Packet to arrive from Receiver or Timeout/////
  	  				
  	  				byte[] ackData = new byte[8];//set up receiving packet
  	  				ackPacket = new DatagramPacket(ackData, ackData.length ); //creating packet for the ACK

  					try {
  						sendSocket.receive(ackPacket);//receive the packet
  						sendSocket.setSoTimeout(timeout);//if ACK not received in the time specified by use continues to catch
  						
  						//if an ACK is sent
  						ackData = ackPacket.getData(); //byte[] of ACK packet sent by receiver

		            	boolean goodACK = decodeAckPacket(ackData); //decode ackPacket 
		            	
		            	if (goodACK){
		            			if (acknoSent == numOfPackets-1){
		            				displayMessage("\n[AckRcvd For]: " + "[" + acknoSent + "] [RECV]\n");
		            				System.out.println("\n[AckRcvd For]: " + "[" + acknoSent + "] [RECV]\n");
		            				displayMessage("\n[LAST PACKET SENT]" + " [" + System.currentTimeMillis() + "]");
		            				System.out.println("\n[LAST PACKET SENT]" + " [" + System.currentTimeMillis() + "]"); //last packet 
			            			break; 	
		            			}
		            			else if(lastSent == numOfPackets && waitingForAck < numOfPackets){
		            				displayMessage("\n[AckRcvd For]: " + "[" + acknoSent + "] [RECV]\n");
		            				System.out.println("\n[AckRcvd For]: " + "[" + acknoSent + "] [RECV]\n");
		            				waitingForAck++; 
		            				waitingForAck = Math.max(waitingForAck, acknoSent); //all packets sent waiting for remaining ACKS
		            			}
		            			else if(acknoSent > waitingForAck){
		            				displayMessage("\n[AckRcvd For]: " + "[" + acknoSent + "] [RECV]\n");
		            				System.out.println("\n[AckRcvd For]: " + "[" + acknoSent + "] [RECV]\n");
		            				waitingForAck = acknoSent+1;
		            			}
		            			else if (acknoSent < waitingForAck){
		            				displayMessage("\n[AckRcvd For]: " + "[" + acknoSent + "] [DuplAck]\n");
		            				System.out.println("\n[AckRcvd For]: " + "[" + acknoSent + "] [DuplAck]\n");
		            			}
		            			else {
		            				displayMessage("\n[AckRcvd For]: " + "[" + acknoSent + "] [MoveWnd]\n");
		            				System.out.println("\n[AckRcvd For]: " + "[" + acknoSent + "] [MoveWnd]\n");
		            				waitingForAck++; 
		            				waitingForAck = Math.max(waitingForAck, acknoSent); //ACK sent -slide window 
		            			}
		            	}
		            	else {
		            		displayMessage("\n[AckRcvd For]: " + "[" + acknoSent  + "]" + " [ErrAck]\n");
			            	System.out.println("\n[AckRcvd For]: " + "[" + acknoSent + "]" + "[ErrAck]\n");
		            	}
  					} 
  					catch (SocketTimeoutException ex) {
			        	   displayMessage("\n[TimeOut]: " + "[" + waitingForAck + "]"  + "\n");
			        	   System.out.println("\n[TimeOut]: " + "[" + waitingForAck + "]"  + "\n");
			        	   
			        	   //time out -all the sent but non-ACKed packets are being re-sent 
			        	   for(int i = waitingForAck; i < lastSent; i++){
								
								//get packets to send from sent list 
								byte[] sendData = sent.get(i);

								//create the packet
								DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, InetAddress.getByName(receiverIPAddress), 
	      									receiverPort);
								
								//send with some probability of loss -hard-coded to zero for easier debugging
								//all Resends will currently go through without any issues
			  	  				if(Math.random() > 0 || lastSent == (numOfPackets - 1)){
			  	  					displayMessage("\n[ReSend]: " + "[ " + i + "] " + message + 
			    							" [" + System.currentTimeMillis() + "]" + " [SENT]\n");
			    					System.out.println("\n[ReSend]: " + "[ " + i + "] " +  message + 
			    							" [" + System.currentTimeMillis() + "]" + " [SENT]\n");
			    					sendSocket.send(sendPacket); 
			  	  				}
								else{
									dropCorruptOrDelay();
									if(drop == false && corruptData == false && delay == false){
					  					displayMessage("\n[SENDing]: " + "[" + lastSent + "] " + message + 
												" [" + System.currentTimeMillis() + "]" + " [SENT]\n");
										System.out.println("\n[SENDing]: " + "[" + lastSent + "] " +  message + 
												" [" + System.currentTimeMillis() + "]" + " [SENT]\n");
										sendSocket.send(sendPacket); 
				  					}
									if (drop) {
										displayMessage( "\n[ReSend]: " + "[ " + i + " ] " +  message +
											" [ " + System.currentTimeMillis() + " ]" + " [ DROP ]\n");
				    					System.out.println("\n[ReSend] : " + "[ " + i + " ] " + message + 
											" [ " + System.currentTimeMillis() + " ]" + " [ DROP ]\n"); 	
				    					drop = false; 
									}
									if(corruptData) {	
				      					byte[] corrupt = corruptPacket(lastSent);
				      					DatagramPacket corruptPacket = new DatagramPacket(corrupt, corrupt.length, 
				      							InetAddress.getByName(receiverIPAddress), receiverPort);
				      					sendSocket.send(corruptPacket);
				      					displayMessage("\n[ReSend]: " + "[ " + i + " ] " + " [ CORRUPTDATAPACKET ]" + 
				      							" [" + System.currentTimeMillis() + "]" + " [ ERRR ]\n");
				      					System.out.println("\n[ReSend]: " + "[ " + i + " ] " + " [ CORRUPTDATAPACKET ]" + 
				      							" [" + System.currentTimeMillis() + "]" + " [ ERRR ]\n");
				      					corruptData = false;
				      				}	
									if(delay) {
				      					displayMessage("\n[SENDing]: " + "[" + lastSent + "] " + "[" + message + "] " + 
				      							" [" + System.currentTimeMillis() + "]" + " [DLYD]\n");
				      					System.out.println("\n[SENDing]: " + "[" + lastSent + "] " + "[" + message + "] " + 
				      							" [" + System.currentTimeMillis() + "]" + " [DLYD]\n");
				      					delayedPacket = sendPacket;//create the delayed packet
				      					msTimeout = (timeout+50);
				      					delayOn = true;
				      					//initiate a thread to delay the sending of packet
				      			        Thread t =  new Thread(new Runnable() {
				      			                public void run() {
				      			                   try {
													waitUntilZero(msTimeout);
				      			                   } catch (InterruptedException | IOException e) {
													e.printStackTrace();
				      			                   } 
				      			                }
				      			        });
				      			        t.start();//start thread 
				      					delay = false; 
				      				}
								}
			  	  				
							}//end for loop
  					}//end catch 
  	  					
  	  			} //end outer while loop -all packets sent
		   		}catch(Exception e){}
		   		finally{
		   			try{
		   			//	sendSocket.disconnect();
		   			//	sendSocket.close();
		   			}
		   			catch(Exception e){}
		   			}
  	  			
	   }//end run()  

   	//////////PRIVATE HELPER METHODS//////////

   	//captures text field data and converts accordingly and sets defaults if needed 
 	private boolean getData() {
 			
 		filePath = filePathField.getText().replace("\\", "\\\\");
		filePath = (filePath.equals("")) ? "C:\\Users\\Ilnaz\\Desktop\\Extract2.txt" : filePath;//for debugging 
		
		String sizeOfPacketStr = sizeOfPacketField.getText();
 		//packet size (total with protocol bytes)  
 		sizeOfPacket = (sizeOfPacketStr.equals("")) ? 512 : Integer.parseInt(sizeOfPacketStr);// set to 512 for debugging  
 		
 		String timeoutStr = timeoutField.getText();
 		timeout = (timeoutStr.equals("")) ? 2000 : Integer.parseInt(timeoutStr);//set to 2000 for debugging
 		
 		String windowSizeStr = windowSizeField.getText();
 		windowSize = (windowSizeStr.equals("")) ? 3 : Integer.parseInt(windowSizeStr);//set to 3 for debugging 
		
		String corruptStr = corruptField.getText();
		disrupt = (corruptStr.equals("")) ? .6 : Double.parseDouble(corruptStr);//set to 0 for debugging  
	
		receiverIPAddress = receiverIPAddressField.getText();
		receiverIPAddress = (receiverIPAddress.equals("")) ? "localhost" : receiverIPAddress;//default set
		
		String receiverPortStr = receiverPortField.getText();
		receiverPort = (receiverPortStr.equals("")) ? 5000 : Integer.parseInt(receiverPortStr);//set to 5000 for debugging
		
		return true;
	}

	//method to convert file to byte[]
	private byte[] readBytesFromFile(String filePath) {

        FileInputStream fileInputStream = null;
        byte[] bytesArray = null;

        try {
            File file = new File(filePath);
            bytesArray = new byte[(int) file.length()];

            //read file into bytes[]
            fileInputStream = new FileInputStream(file);
            fileInputStream.read(bytesArray);

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (fileInputStream != null) {
                try {
                    fileInputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return bytesArray;
	}
	 
	//converting byte[] into List<byte[]> of specified size + and empty byte[] at the end 	
	private List<byte[]> splitBytesArray(byte[] originalArray, int sectionSize) {
		
		 List<byte[]> listOfByteArrays = new ArrayList<byte[]>();
		 int totalSize = originalArray.length;
		 if(totalSize < sectionSize ){
		    sectionSize = totalSize;
		 }
		 int from = 0;
		 int to = sectionSize;
		 
		 while(from < totalSize){
		     byte[] partArray = Arrays.copyOfRange(originalArray, from, to);
		     listOfByteArrays.add(partArray);

		     from += sectionSize;
		     to = from + sectionSize;
		     if(to>totalSize){
		         to = totalSize;
		     }
		 }		 
		 byte[] endPacket = new byte[0]; 
		 listOfByteArrays.add(endPacket);//append empty packet to end stream 
		 return listOfByteArrays;
	}

	//method to create a list<byte[]> that contains protocol as well as data
	private List<byte[]> byteArrayWithProtocol(List<byte[]> originalArray) {
		
		List<byte[]> listOfByteArrays = new ArrayList<byte[]>();
			
		for (int i = 0; i < originalArray.size(); i++){
			int ackNumber = originalArray.size();//ackNumber holds total number of packets being sent 
			int sequenceNumber = i;
			short checksum = 0;		
			byte[] checksumArray = shortToBytes(checksum);//convert checksum into byte[]
			byte[] data = originalArray.get(i);//byte[] of data
			short length = (short) (12 + data.length);//length of byte[] variable for last packet 	
			byte[] lengthArray = shortToBytes(length);//convert length into byte[]
			byte[] ackNumberArray = intToBytes(ackNumber);//convert ackNumber into byte[]
			byte[] sequenceNumberArray = intToBytes(sequenceNumber);//convert squenceNumber into byte[]
			//byte[] of protocol and data	
			byte[] protocalAndData = concat(checksumArray, lengthArray, ackNumberArray, sequenceNumberArray, data);
			listOfByteArrays.add(protocalAndData);		
		}
		return listOfByteArrays; 
	}

	//Method to decode ACK packet  
	private boolean decodeAckPacket(byte[] data){
		
		byte[] checksumSent = Arrays.copyOfRange(data, 0, 2);
		byte[] ackNumberSent = Arrays.copyOfRange(data, 4, 8);
		
		short checksumValueSent = bytesToShort(checksumSent);
		
		acknoSent = bytesToInt(ackNumberSent);								
		
		if (checksumValueSent != 0){
			return false;  //corrupt ACK 
		}				
				
		return true; 
	}
	
	//creates corrupt packet byte[] for sending to receiver
	private byte[] corruptPacket(int sequenceNumber){
		
		byte[] checksum = shortToBytes((short) 1);//bad checksum = 1  
		byte[] length = shortToBytes((short) 8);
		byte[] ackNumber = intToBytes(numOfPackets);
		byte[] sequenceNum = intToBytes(sequenceNumber);
		
		byte[] finalPacket = concat(checksum, length, ackNumber, sequenceNum);

		return finalPacket;
		
	}
	
	//method will wait for specified amount of time and then send delayed packet
	private synchronized void waitUntilZero(long msTimeout) throws InterruptedException, IOException {

        long msEndTime = System.currentTimeMillis() + msTimeout;
        long msRemaining = msTimeout;
        while (msRemaining > 0) {
            msRemaining = msEndTime - System.currentTimeMillis();
            delayOn = true; 
        }
        sendSocket.send(delayedPacket);
        delayOn = false; 
    }

	/////METHODS USING ByteBuffer FOR CONVERTING PRIMATIVE VALUES TO BYTE[] AND VICE VERSA  -BIG ENDIAN/////
	
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
		
	//method to concatenate byte[]'s 
	private byte[] concat(byte[]...arrays) {
	    // Determine the length of the result array
	    int totalLength = 0;
	    for (int i = 0; i < arrays.length; i++) {
	        totalLength += arrays[i].length;
	    }

	    // create the result array
	    byte[] result = new byte[totalLength];

	    // copy the source arrays into the result array
	    int currentIndex = 0;
	    for (int i = 0; i < arrays.length; i++) {
	        System.arraycopy(arrays[i], 0, result, currentIndex, arrays[i].length);
	        currentIndex += arrays[i].length;
	    }
	    return result;
	}

 	//will set drop, corruptData, or delay to true 
 	private void dropCorruptOrDelay(){
 		Random randomGenerator = new Random();
 		int random = randomGenerator.nextInt(3); 
 		
    	if(random == 0){
    		drop = true;
    	}
    	if(random == 1){
    		corruptData = true;   
    	}
    	if(random == 2 && lastSent < numOfPackets-1 && delayOn != true ){
    		delay = true;   
    	}
	}
 	
   //method to update displayArea
   private void displayMessage(final String messageToDisplay) {
      SwingUtilities.invokeLater(
         new Runnable() {
            public void run() { 
               displayArea.append(messageToDisplay); // display message
            } 
         } 
      ); 
   } // end method displayMessage
 	
	 	
}//end class 
