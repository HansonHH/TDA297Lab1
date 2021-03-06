import mcgui.*;

import java.util.*;
import java.util.Map.Entry;


/**
 * Implementing a reliable and ordered multicast protocol
 *
 * @author Group 11
 */
public class ExampleCaster extends Multicaster {

	int Rg; // Sequence number of a normal node
	int Sg; // Sequence number of a sequencer
	int sequencer_id; // Sequencer's id
	HashMap<String, ExtendMessage> HoldBackQueue; // The hold-back queue to store the messages that we have received but not delivered
	HashMap<String, ExtendMessage> ReceivedMessages; // Stores all the messages which have received
	HashMap<String, ExtendMessage> ReceivedOrders; // Stores all the orders which have received
	int ALIVE; // State of a alive node
	int CRASHED; // State of a crashed node
	int[] nodesStatus; // Store the status of nodes. 0 = ALIVE 1 = CRASHED
	int[] nodeClocks; // Store the vector clocks
	
	
    /**
     * No initializations needed for this simple one
     */
    public void init() {
    	
        Rg = 0;
        Sg = 0;
        sequencer_id = hosts -1;
        HoldBackQueue = new LinkedHashMap<String, ExtendMessage>();
        ReceivedMessages = new LinkedHashMap<String, ExtendMessage>();
        ReceivedOrders = new LinkedHashMap<String, ExtendMessage>();
        ALIVE = 0;
        CRASHED = 1;
        nodesStatus = new int[hosts];
        nodeClocks=new int[hosts];
        // Initiate the vector clock
        for(int i=0;i<hosts;i++){
        	nodeClocks[i] = 0;
        }
        
        mcui.debug("The network has "+hosts+" hosts!");
        if(isSequencer()){
        	mcui.debug("I'm the sequencer.");
        }
        else{
        	mcui.debug("The sequencer is "+sequencer_id);
        }
        
    }
    
    // Check if the node is the current sequencer
    private boolean isSequencer() {
        if(id == sequencer_id){
        	return true;
        }
        else{
        	return false;
        }
    }
    
    // Create unique id for message
    private String createUniqueId(){
    	String unique_id = UUID.randomUUID().toString().replaceAll("-", "");
    	return unique_id;
    }
    
    // B-Multicast message to nodes
    private void multicast(ExtendMessage message){
    	for(int i=0; i < hosts; i++) {
    		/* Sends to everyone except crashed node */
    		if(nodesStatus[i] != CRASHED){
    			bcom.basicsend(i,message);
    		}
        }
    }
    
        
    /**
     * The GUI calls this module to multicast a message
     */
    // For process to R-multicast message to group
    public void cast(String messagetext) { /* messagetext is the input from UI */
    	String message_id = createUniqueId();
    	nodeClocks[id]++;
    	ExtendMessage message = new ExtendMessage(id, messagetext, message_id,ExtendMessage.TYPE_MESSAGE,createClockMessage());
    	multicast(message); 	
        mcui.debug("Sent out: \""+messagetext+"\"");
    }
    
    // Check if the message has been received already
    private boolean isReliableMulticast(ExtendMessage received_message){
    	/* If the received message is the type of MESSAGE */
    	if(received_message.getType() == ExtendMessage.TYPE_MESSAGE){
    		// The message received has already been put in ReceivedMessages queue
    		if(ReceivedMessages.containsKey(received_message.getIdNumber()) == true){
        		return true;
        	}
    		// The message received has not been put in ReceivedMessages queue
        	else{
        		// R-multicast the received message to all nodes for reliability
    			multicast(received_message);
        		return false;
        	}
    	}
    	/* If the received message is the type of TYPE_SEQ_ORDER */
    	if(received_message.getType() == ExtendMessage.TYPE_SEQ_ORDER){
    		// The message received has already been put in ReceivedOrders queue
    		if(ReceivedOrders.containsKey(received_message.getIdNumber()) == true){
        		return true;
        	}
    		// The order received has not been put in ReceivedOrders queue
        	else{
        		// R-multicast the received message to all nodes for reliability
    			multicast(received_message);
        	}
    	}
    	return false;
    }
    
    // Create node's ClockMessage for transferring 
    private String createClockMessage(){
    	String Clocks="";
    	for(int i =0;i<hosts;i++){
    		if(i==0){
    			Clocks = Clocks + nodeClocks[i];
    		}
    		else{
    			Clocks = Clocks + "#" + nodeClocks[i];
    		}
    	}
    	return Clocks;
    }
    
    // Parse ClockMessage and return node's clock value
    private int parseClockMessage(ExtendMessage received_message,int node_id){
    	return Integer.parseInt(received_message.getClocks().split("#")[node_id]);
    }
    
    // Check whether the sequencer has received any message that the message sender had delivered at the time it multicast the message
    private boolean checkReceivedAnyMessageFromSender(ExtendMessage received_message){
    	for(int i=0;i<hosts;i++){
    		if(i!= received_message.getSender()){
    			// If the sequencer hasn't receive any message that the message sender had delivered at the time it multicast the message
    			if(parseClockMessage(received_message,i) > nodeClocks[i]){
        			return false;
        		}
    		}
    	}
    	return true;
    }
    
    // Create order message and multicast it
    private void createOrderMulticast(ExtendMessage received_message){
    	ExtendMessage order = new ExtendMessage(received_message.getSender(), received_message.getText()+"/"+String.valueOf(Sg), received_message.getIdNumber(),ExtendMessage.TYPE_SEQ_ORDER,createClockMessage());
    	// Multicast order to other nodes
    	multicast(order);
	    // Increment Sequqncer Number by 1
	    Sg = Sg + 1;
    }
    
    // Generates Sequencer Number and multicasts to other nodes
    private void generateSeqMulticast(ExtendMessage received_message){
    	// Get message sender's id	
    	int sender_id = received_message.getSender();
    	while(true){
    		// If the multicast message is not from the sequencer then the sequencer decide whether to multicast order
    		if(sender_id != sequencer_id){
    			// If the sequencer has received all the previous messages from the sender
	    		if(parseClockMessage(received_message,sender_id) == nodeClocks[sender_id] +1 && checkReceivedAnyMessageFromSender(received_message) == true){
	    			// Increment vector clock by 1
	    			nodeClocks[sender_id]++;
	    			// Create order and multicast it to all nodes
	    			createOrderMulticast(received_message);
	    	    	break;
	    		}
	    	}
	    	// If the multicast message is from the sequencer then the sequencer multicast order to all nodes
	    	else{
	    		// Create order and multicast it to all nodes
		    	createOrderMulticast(received_message);
		   	    break;
	   		} 		
    	}
    }
    
    // Deliver message
    private void deliverMessage(ExtendMessage received_message){
    	while(true){	
    		if(HoldBackQueue.containsKey(received_message.getIdNumber()) && Rg == Integer.valueOf(received_message.getText().split("/")[1])){
    	        // Remove the message from HoldBackQueue
    			HoldBackQueue.remove(received_message.getIdNumber());
        		// Get message sender's id	
    			int sender_id = received_message.getSender();
    			// If the receiver is not sequencer and the message sender then increments its vector clock by 1 according to the message sender			
    			if( id != sender_id && isSequencer() == false ){
    				nodeClocks[sender_id]++;
    			}
        		
    			mcui.deliver(received_message.getSender(), received_message.getText().split("/")[0]);
        		Rg = Integer.valueOf(received_message.getText().split("/")[1]) + 1;

        		break;
    		}
    	}
    }
    
    /**
     * Receive a basic message
     * @param message  The message received
     */
    public void basicreceive(int peer,Message message) {
    	
    	ExtendMessage received_message = (ExtendMessage) message;

    	// If message has been received, then return
    	if(isReliableMulticast(received_message) == true){ 
    		return;
    	}
    	// If the message is text message
    	if(received_message.getType() == ExtendMessage.TYPE_MESSAGE){
    		// Put the current received message in ReceivedMessages
    		ReceivedMessages.put(received_message.getIdNumber(),received_message);
    		// Put the current received message in HoldBackQueue
			HoldBackQueue.put(received_message.getIdNumber(),received_message);
			// If the receiver is the sequencer
			if(isSequencer()){
				generateSeqMulticast(received_message);
			}
    	}
    	// If the message if order message
    	if(received_message.getType() == ExtendMessage.TYPE_SEQ_ORDER){
    		// Put the current received order in ReceivedOrders
			ReceivedOrders.put(received_message.getIdNumber(),received_message);
			deliverMessage(received_message);
    	}

    }

    // Set new sequencer if the previous sequencer crashes
    private void setNewSequencer(){
    	// 
    	for (int i = hosts - 1; i >= 0; i--){
    		if (nodesStatus[i] == ALIVE) {
    			sequencer_id = i;
                break;
            }
    	}
    	if(isSequencer()){
        	mcui.debug("I'm the new sequencer.");
        	// The new sequencer to handle the crashing problem of the previous sequencer
        	actAsNewSequencer();
        }
        else{
        	mcui.debug("The new sequencer is "+sequencer_id);
        }
    }
    
    // The new sequencer need to handle the message that are not delivered because of the crash of the previous sequencer
    private void actAsNewSequencer(){
    	// Find the lowest logic clock of the new sequencer when the previous sequencer dies
    	int minimal = 0;
    	for(Entry<String, ExtendMessage> entry:HoldBackQueue.entrySet()){
    		int temp = parseClockMessage(entry.getValue(),id);
    		if(minimal == 0){
    			minimal = temp;
    		}
    		if(minimal > temp){
    			minimal = temp;
    		}
		}
    	// Set the new sequencer's logic clock back to the previous moment when the previous crashed
    	// If the new sequencer delivered some messages after the previous sequencer crashed
    	if(minimal != nodeClocks[id]){
    		//last_logic_clock = minimal - 1;
    		//nodeClocks[id] = last_logic_clock;
    		nodeClocks[id] = minimal - 1;
    	}
    	// Set new sequencer's Sg equals to its Rg
    	Sg = Rg;
    	// Generate Sequencer and Multicast
    	for(Entry<String, ExtendMessage> entry:HoldBackQueue.entrySet()){
    		// If the message in the HoldBackQueue is sent by this node
    		if(entry.getValue().getSender() == id){
    			nodeClocks[id]++;
    		}
    		// Generates Sequence Number and multicasts to other nodes
    		generateSeqMulticast(entry.getValue());
    	}
    }
    
    /**
     * Signals that a peer is down and has been down for a while to
     * allow for messages taking different paths from this peer to
     * arrive.
     * @param peer	The dead peer
     */
    public void basicpeerdown(int peer) {
        mcui.debug("Node "+peer+" crashes!");
        nodesStatus[peer] = CRASHED;
        // If the sequencer crashes, then set a new sequencer
        if(peer == sequencer_id){
        	setNewSequencer();
        }
    }
      
    
}


class ExtendMessage extends Message {
    
    String text;
    String id_number;
    int type;
    String Clocks;
    static final int TYPE_SEQ_ORDER = 1;
    static final int TYPE_MESSAGE = 0;
        
    public ExtendMessage(int sender,String text,String id,int type,String Clocks) {
        super(sender);
        this.text = text;
        this.id_number = id;
        this.type = type;
        this.Clocks = Clocks;
    }
    
    /**
     * Returns the text of the message only. The toString method can
     * be implemented to show additional things useful for debugging
     * purposes.
     */
    public String getText() {
        return text;
    }
    public String getIdNumber() {
        return id_number;
    }
    public int getType() {
        return type;
    }
    public String getClocks() {
        return Clocks;
    }
    
    public static final long serialVersionUID = 0;
}