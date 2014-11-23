/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */


/**
 *
 * @author HARSH, VISHAL
 */


import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.nio.*;


public class Server
{
    DatagramSocket server_socket;
    
    String file_name;
    static String file_path="F:\\1 NCSU\\IP\\IP project 2\\ServerFiles\\";
    float p;
    FileOutputStream fstream=null;
    File input_file=null;
    Thread recv_pkt;
    AtomicBoolean state= new AtomicBoolean(false);
    AtomicBoolean re_packet= new AtomicBoolean(false);
    
    volatile InetAddress client_ip;
    volatile int client_port;
    static long old_seq=0;
    static long ack_no=0;
    static int port=65400;
    
    
    
    public static void main(String args[]) throws Exception
    {
        try
        {
                Server obj = new Server();
                obj.server_socket=new DatagramSocket(port);  

                Scanner s=new Scanner(System.in);
                System.out.print("Enter the file name :");
                obj.file_name=s.next();
                file_path+=obj.file_name;
                obj.input_file=new File(file_path);



                System.out.print("Enter the packet loss probability :");
                obj.p=s.nextFloat();
                obj.start_fun();
                
        }
        
        catch(Exception e)
        {
            System.out.println(e);
        }
        
    }
    
    
    public void start_fun()
    {
        recv_pkt = new Thread(new ReceivePacket(this));
        System.out.println("Waiting for Packets");
        recv_pkt.start();
        
    }
    

    
    
    class SendPacket implements Runnable
    {
        byte send_buff[]=new byte[700];
        DatagramSocket serv_socket;
        
        String ack_indicator="1010101010101010";
        String chksum_field="0000000000000000";
        Server server_obj=null;
        
        public SendPacket(Server serv_obj)
        {
            server_obj=serv_obj;
            serv_socket=serv_obj.server_socket;
        }
        
        
        public synchronized void run()
        {
            try
            {
                
                InetAddress cl=serv_socket.getLocalAddress();
                int p=serv_socket.getLocalPort();

                if(server_obj.re_packet.get()==false)
                    ack_no+=1;
                        
                String getAckHeader = makeHeader(ack_no, chksum_field,ack_indicator);
                byte[] dataPDU=getAckHeader.getBytes();
                DatagramPacket packet_sent= new DatagramPacket(dataPDU,dataPDU.length, client_ip, client_port);
                serv_socket.send(packet_sent);
                server_obj.state.set(false);
            }
             
            catch(Exception e)
            {
                System.out.println(e);
            }
        }
         
        
        //===========================ADD ACK NO, CHECKSUM AND ACK INDICATOR TO HEADER==============================
        
        public String makeHeader(long ack, String checksum,String ack_field)
        {
            String tempAck="00000000000000000000000000000000";
            String binaryAck=Long.toBinaryString(ack);
            binaryAck=tempAck.substring(0, (32-(binaryAck.length())))+binaryAck;
            
            String AckPacket=binaryAck+checksum+ack_field;
            
            return AckPacket;
        }
    }
    
    
    
    
    class ReceivePacket implements Runnable
    {
        byte recv_buff[]=new byte[70000];
        
        float pkt_loss_p;
        DatagramSocket serv_socket;
        Server server_obj=null;
        
        public ReceivePacket(Server serv_obj)
        {
            server_obj=serv_obj;
            pkt_loss_p=serv_obj.p;
            serv_socket=serv_obj.server_socket;
        }
        
        
        public synchronized void run()
        {
            try
            {
                
                server_obj.fstream = new FileOutputStream(server_obj.input_file);

                while(true)
                {
                    DatagramPacket packet_recv= new DatagramPacket(recv_buff,recv_buff.length);
                    serv_socket.receive(packet_recv);
                    client_ip = packet_recv.getAddress();
                    client_port = packet_recv.getPort();
                    
                    Random r= new Random();
                    float random = r.nextFloat();
                    //System.out.println("Random value : "+random);
                    
                    
                    byte[] readData = packet_recv.getData();//get the bytes
                    String readDataString = new String(readData);

                    String actualDataString = readDataString.substring(0, packet_recv.getLength());
                    String[] binaryData=make2Bytes(actualDataString);
                    int len=binaryData.length;
                    String seq_num = binaryData[0]+binaryData[1];
                    long seq_int=Long.parseLong(seq_num,2);
                    
                    
                    if(random>pkt_loss_p)
                    {
                            
                           
                            long result = UDPChecksum(binaryData,len);
                            String stringData = getStringData(actualDataString);
                            boolean error=false;
                            
                            if(result==0)
                            {
                                System.out.println("Packet received with no errors!");
                                error=false;
                            }
                            else
                            {
                                System.out.println("Packet received WITH ERRORS!");
                                error=true;
                            }

                            
                            //System.out.println("seq num in int: "+seq_int);
                            //System.out.println("old seq: "+old_seq);
                            if(error==false)
                            {
                                    if(seq_int==0)
                                        System.out.println("Sequence no 1!"); 
                                    else if(seq_int==(old_seq+1))
                                    {
                                        System.out.println("Packet in sequence!");
                                        byte[] byteString = stringData.getBytes();
                                        server_obj.fstream.write(byteString);
                                        server_obj.state.set(true);//=true;
                                        server_obj.re_packet.set(false);


                                        Thread send=new Thread(new SendPacket(server_obj));
                                        send.start();
                                        send.join();
                                        old_seq=seq_int;
                                    }
                                    else 
                                    {
                                        server_obj.state.set(true);
                                        server_obj.re_packet.set(true);
                                        Thread send=new Thread(new SendPacket(server_obj));
                                        send.start();
                                        send.join();
                                        old_seq=seq_int;
                                    }
                                 
                            }
                           
                        }
                        else
                            System.out.println("Packet Loss Occurred, Sequence No = "+seq_int);
                    }
            }
            
            catch(Exception e)
            {
                System.out.println(e);
            }
            
        }
        
        
        public String getStringData(String binaryData)
        {
            int len=binaryData.length();
            
            int len2=len/8;
            String[] BytesOf8=new String[(len2)];
            char[] character = new char[len2];
            String result;
           
            for(int i=0;i<len2;i++)
            {
                BytesOf8[i]=binaryData.substring(i*8, (i*8+8));
                
            }
            int count=0;
            
            for(int i=9;i<len2;i++)
            {
                character[count]=(char)(Integer.parseInt(BytesOf8[i],2));
                count+=1;
                
            }
            result=Character.toString(character[0]);
            
            for(int i=1;i<count;i++)
            {
                result+=Character.toString(character[i]);
                
            }
               
            return result;
        }
        
        
        public String[] make2Bytes(String A)
        {
            int len=(A.length()/16);
          
            String[] BytesOf8 = new String[len];
            for(int i=0;i<len;i++)
            {
                BytesOf8[i]=A.substring(i*16, (i*16+16));
         
            }
            return BytesOf8;
            
        }
        
          
        
        long UDPChecksum(String[] data, int count)
        {
            String result="0000000000000000";
            for(int j=0;j<count;j++)
            {
                result=BinaryAdditionString(result, data[j]);
            }
            
            result=OneComplement(result);
            long out=Integer.parseInt(result, 2);
            return out;
        }

        
        
        public String BinaryAdditionString(String A, String B)
        {
            char[] A_char=A.toCharArray();
            char[] B_char=B.toCharArray();
            char sum='0';
            char carry='0';
            for(int j=(A.length()-1);j>=0;j--)
            {
                sum=XOR(A_char[j],XOR(B_char[j],carry));
                carry=XOR((char)(A_char[j]&B_char[j]),((char)(carry&(XOR(A_char[j],B_char[j])))));
                A_char[j]=sum;
            }
            
            sum='0';
            //If carry is 1 after final addition
            for(int j=(A.length()-1);j>=0;j--)
            {
                if(carry=='1')
                {
                    sum=XOR(A_char[j],carry);
                    carry=(char)(A_char[j]&carry);
                    A_char[j]=sum;
                }
            }
        
            String A_str=String.valueOf(A_char);
            return A_str;
        }
        
        
                
    //===================================== X  O  R =================================================================
    
    
    public char XOR(char a, char b)
    {
        int c=a^b;
       
        if(c==0)
            return '0'; //d='0';
        else
            return '1';//                    d='1';
    }
    
        
        
        public char OR(char a, char b)
        {
            int c=a|b;
            char d=(char)c;
            
            return d;
        }
        
        
        
        public String OneComplement(String A)
        {
            char[] A_char=A.toCharArray();
            for(int j=0;j<A.length();j++)
            {
                A_char[j]=NOT(A_char[j]);
                
            }
            
            String A_str=String.valueOf(A_char);
            return A_str;
        }
        
        
         //===================================== N  O  T =================================================================
    
    
        public char NOT(char a)
        {
            if(a=='1')
                return '0'; 
            else
                return '1';
        }

    
        
        

    }
}
