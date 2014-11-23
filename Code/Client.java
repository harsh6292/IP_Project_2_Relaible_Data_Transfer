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
import java.nio.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;


public class Client
{
    
        DatagramSocket client_socket[] = null;
        PrintWriter out = null;
        BufferedReader in = null;
	BufferedReader stdIn = null;
        String hostname=null;
        static int num_of_serv;
        boolean acked_flag=true;
        String server_list[]=new String[num_of_serv];
        long old_seq_no_acked=0;
        int server_port_list[]=new int[num_of_serv];
        int server_port=0;
        int MSS=1000;
        String file_name;
        static String file_path="C:\\Users\\HARSH\\Downloads\\";
        volatile boolean timer=false;
        
        
          
        Thread [] th=new Thread[10];
            
        
        
       static AtomicBoolean[] ack_flag=new AtomicBoolean[10] ;
       static AtomicBoolean[] state=new AtomicBoolean[10];
       static AtomicBoolean timout_expired[]=new AtomicBoolean[10];
       DatagramSocket send_sock[]=new DatagramSocket[server_list.length];
       DatagramPacket data_packet[] = new DatagramPacket[server_list.length];
 
        
        public Client()
                
        {
            hostname="localhost";
        }
        
        
        public void initizalize() throws Exception
        {
            
            Scanner s=new Scanner(System.in);
            
            
            //long TimeSeconds = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis());    
            System.out.print("Enter number of servers : ");
            num_of_serv=s.nextInt();
            
            
             th=new Thread[num_of_serv];
             
             
            server_list=new String[num_of_serv];
            server_port_list=new int[num_of_serv];
            data_packet=new DatagramPacket[num_of_serv];
            send_sock=new DatagramSocket[num_of_serv];
            
            
          /*  server_list[0]="152.46.17.190";
            server_list[1]="152.46.18.185";
            server_list[2]="152.46.18.86";*/
            
            for(int i=0;i<num_of_serv;i++)
            {
                System.out.print("Enter hostname of server "+(i+1)+" : ");
                server_list[i]=s.next();
                
                
            }
                   
                   
            System.out.print("Enter port number of all servers : ");
            server_port=Integer.parseInt(s.next());
            
            for(int i=0;i<num_of_serv;i++)
            {
                server_port_list[i]=server_port;
            }
            
            System.out.print("Enter the file name to be sent : ");
            file_name=s.next();
            file_path+=file_name;
            System.out.println("File Path : "+file_path);
            
            for(int count=0;count<num_of_serv;count++)
            {
                ack_flag[count]=new AtomicBoolean(false);
                state[count]=new AtomicBoolean(false);
                timout_expired[count]=new AtomicBoolean(false);
                send_sock[count]=new DatagramSocket();
                send_sock[count].setSoTimeout(100);
            }
            
            System.out.print("Enter the  MSS : ");
            MSS=s.nextInt();
        }
        
        
        public static void main(String args[]) throws Exception
        {
            Client obj = new Client();
            obj.initizalize();
            obj.callerFunction();
           
        }
        
        
        public void callerFunction()
        {
            Thread thread=new Thread(new ClientSender(this));
            thread.start();
        
        }


    public class ClientSender implements Runnable 
    {
        
        String local_read_file,dataPDU;
        String padding="0101010101010101";
        String byte_padding="00000000";
        String temp_char_value=new String();
        int sequenceNo=1;
        int local_mss;
        int file_empty=1;
        Client obj;
        boolean proceed_sending=true;

        public ClientSender(Client client_obj)
        {
            local_mss=client_obj.MSS;
            obj=client_obj;
        }
        
        public void run() 
        {
            try
            {
                File input_file=new File(file_path);
                FileInputStream fstream = new FileInputStream(input_file);
                
                byte[] read_bytes= new byte [local_mss];

                //long startTimeSeconds = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis());
                //System.out.println("File sending started : "+startTimeSeconds);
               
                while(file_empty!=0&&proceed_sending==true&& fstream.available()>0)
                {

                    for(int count=0;count<num_of_serv;count++)
                    {
                        ack_flag[count].set(false);
                    }
                    
                    file_empty=fstream.read(read_bytes,0,local_mss);
                    int k=0,arr_len=0;
                    String file_contents = new String(read_bytes);
                    System.out.println("Packet sent, Sequence No : "+ sequenceNo);
                    temp_char_value="/0";
                    for (int i=0;i<file_contents.length();i++)
                    {
                        
                        String temp=Integer.toBinaryString((int)file_contents.charAt(i));

                        if(temp.length()<8)
                        {
                            temp=byte_padding.substring(0,8-temp.length()).concat(temp);
                        }
                        
                        if(temp_char_value.equals("/0"))
                        {
                            temp_char_value=temp;
                        }
                        temp_char_value=temp_char_value+temp;
                    }
                    
                    dataPDU=null;
                    dataPDU=composeHeader(sequenceNo,padding,temp_char_value);
                    String each2Bytes[]=make2Bytes(dataPDU);

                    String checksum_result=udpChecksum(each2Bytes);  

                    //System.out.println("CHECKSUM VALUE :" + checksum_result);
                    char[] replace_checksum=dataPDU.toCharArray();


                    for(int i=32;i<48;i++)
                    {
                        replace_checksum[i]=checksum_result.charAt(i-32);
                    }
                    dataPDU=String.valueOf(replace_checksum);
           
                    Thread th_send=new Thread(new CallRdt(dataPDU ,obj.server_list,obj.server_port_list,obj.server_list.length));
                    th_send.start();
                    th_send.join();


                    for(int c=0;c<obj.server_list.length;c++)
                    {   
                        ClientReceiver client_receiver=new ClientReceiver(obj,c,sequenceNo);
                        th[c]=new Thread(client_receiver);
                        th[c].start();
                    }  

                    for (k=0;k<obj.server_list.length;k++)
                    {
                        th[k].join();
                    }

                    try
                    {

                        for(int i=0;i<server_list.length;i++)
                        {
                            proceed_sending=ack_flag[i].get();
                            if(proceed_sending==false)
                                break;
                        }

                        while(proceed_sending==false)
                        {
                            int count=0;
                            int[] noreply=new int[num_of_serv];
                            
                            //These servers didn't reply
                            for(int i=0;i<num_of_serv;i++)
                            {
                                if(ack_flag[i].get()==false)
                                {
                                    noreply[count]=i;
                                    count++;
                                }
                            }
                            
                            //System.out.println("Total server that didnt reply : "+count);
                            CallRdt resend=new CallRdt(dataPDU, server_list, server_port_list,num_of_serv);
                            Thread resendth=new Thread(resend);
                            resendth.start();
                            resendth.join();


                            for(int c=0;c<noreply.length;c++)
                            {   
                                ClientReceiver client_receiver=new ClientReceiver(obj,noreply[c],sequenceNo);
                                th[c]=new Thread(client_receiver);
                                th[c].start();
                            }  

                            for (k=0;k<noreply.length;k++)
                            {
                                th[k].join();
                            }

                            for(int i=0;i<server_list.length;i++)
                            {
                                proceed_sending=ack_flag[i].get();
                                if(proceed_sending==false)
                                    break;
                            }
                        }


                        if(proceed_sending==true)
                        {
                            old_seq_no_acked=sequenceNo;
                            sequenceNo++;
                        }

                    }

                    catch(Exception e)
                    {
                        System.out.println(e);
                    }
                }
                
                //long endTimeSeconds = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis());
                //System.out.println("start time : "+startTimeSeconds+"\nFile sending finished : "+endTimeSeconds+"\n Total time taken : "+(endTimeSeconds-startTimeSeconds));
            }
            catch(Exception e)
            {
                System.out.println(e);
            }
            
        }

        

      //---------------MAKE 16 BYTES OF DATA--------------------------------------  
        
        public String[] make2Bytes(String file_contents)
        {
                String each2Bytes[]=new  String[(file_contents.length()/16)];
                for(int j=0;j<(file_contents.length()/16);j++)
                {
                    each2Bytes[j]=file_contents.substring(j*16,j*16+16);
                }
                
                return each2Bytes;
        }

        
        
        //==========CALCULATE UDP CHECKSUM=========================================

        public String udpChecksum(String each2Bytes[])
        {
            String result="0000000000000000";
            for(int i=0;i<each2Bytes.length;i++)
            {
                char[] bit_stream1=each2Bytes[i].toCharArray();
                result=binaryAddition(result,each2Bytes[i]);

            }
            result=OneComplement(result);
           
            return result;
        }

        
        
        //---------------------FIND ONE'S COMPLEMENT OF SUM OF BYTES-------------------
        
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

        
        //====================================ADD SEQUENCE NUMBER, CHECKSUM TO HEADER=====================================
        
        public String composeHeader(int sequenceNo,String padding,String file_contents)
        {
            String initial_checksum="0000000000000000";
            String pad2four_bytes="00000000000000000000000000000000";
            String binarySeqNo=Integer.toBinaryString(sequenceNo);
            binarySeqNo=pad2four_bytes.substring(0,32-binarySeqNo.length())+binarySeqNo;
            String data_packet=binarySeqNo+initial_checksum+padding+file_contents;
            return data_packet;

        }
        
        
        //=====================================PERFORM BINARY ADDITION OF TWO 16-BIT STRINGS====================================

        public String binaryAddition(String final_result,String each2Bytes)
        {
            char[] binary1=each2Bytes.toCharArray();
            char[] result=final_result.toCharArray();
            char bit_result='0';
            char carry='0';
            for(int j=15;j>=0;j--)
            {
                bit_result= xor(carry,(xor(binary1[j],result[j])));
                carry= xor((char)(binary1[j]&result[j]),((char)(carry&(xor(binary1[j],result[j])))));
                result[j]=bit_result;
            }
            for(int j=15;j>=0;j--)
            {
                if(carry=='1')
                {
                    char sum=result[j];
                    result[j]=xor(result[j],carry);
                    carry=(char)(sum&carry);
                }
            }

            final_result=String.valueOf(result);                      // convert char array to String
            return final_result;
        } 

        
      //===============================FIND XOR OF A BIT===========================================================
        
        public char xor(char bit_position,char carry)
        {
            int ret_value;
            ret_value=(bit_position^carry);

            if(ret_value==0)
               return '0';
            else 
               return '1';
        }


        
        
        //=====================CLASS TO CALL THE RDT FUNCTION=======================================================
        
        public class CallRdt implements Runnable
        {
            String dataPDU;
            String[] server_ip;
            int[] server_port;
            int token;
            public void CallRdt()
            {

            }
            public CallRdt(String dataPDU, String[] server_ip, int[] server_port,int c)
            {
                this.dataPDU=dataPDU;
                this.server_ip=server_ip;
                this.server_port=server_port;
                this.token=c;
            }
            public void run()
            {
                try
                {
                    for(int i=0;i<token;i++)
                    {
                        if(ack_flag[i].get()==false)
                        {
                            byte[] send_data=dataPDU.getBytes();
                            InetAddress inetAddr=InetAddress.getByName(server_ip[i]);
                            
                            data_packet[i]=new DatagramPacket(send_data,send_data.length,inetAddr, server_port[i]);
                                
                            send_sock[i].send(data_packet[i]);
                            state[i].set(true);
                                  
                        }
                    }
                }
                
                catch(Exception e)
                {
                    System.out.println(e);
                }
            }
        }
    }


    
    
    //===================RECEIVER CLASS TO ACCEPT ACKNOWLEDGEMENTS====================================
    
    public class ClientReceiver implements Runnable
    {
        DatagramSocket datagram_sock;   
        Client obj;
        int recv_ack_count=0;
        int token;
        DatagramPacket recv_pkt;
        AtomicBoolean  received_response=new AtomicBoolean();    //Individual received responses from each server
        AtomicBoolean timer_expired=new AtomicBoolean();    //Individual received responses from each server
        boolean timed_out=false;
        byte[] ack_recv=new byte[256];
        int seqNo=0;
        
        public ClientReceiver() 
        {

        }

        
        public ClientReceiver(Client c_obj,int token, int recv_seq_no)
        {
            this.token=token;
            obj=c_obj;
            seqNo=recv_seq_no;
        }
                
        public  void run()
        {
            
            received_response=new AtomicBoolean(false);
            timer_expired=new AtomicBoolean(false);

            try
            {
            
                if(state[token].get()==true)
                {

                    int i=0;
                    recv_pkt=new DatagramPacket(ack_recv, 100);
                    do
                    {
                        try
                        {
                            if(received_response.get()==false)
                            { 
                                send_sock[token].receive(recv_pkt);
                                received_response.set(true);
                                ack_flag[token].set(true);
                                state[token].set(false);
                                recv_ack_count++;

                            }
                        }

                        catch(Exception e)
                        {
                            System.out.println("Timeout, Sequence Number = "+seqNo);
                            timed_out=true;
                            received_response.set(false);
                        }

                    }while((recv_ack_count==0&&timed_out==false));


                    //COMMENTEDD FOR TESTING CHECKS ACK 
                    if(received_response.get()==true)
                    {
                        byte[] received_ack_bytes=recv_pkt.getData();
                        String received_ack_string=new String(received_ack_bytes);

                        String actual_ack_string=received_ack_string.substring(0,recv_pkt.getLength());
                        if(actual_ack_string.substring(48,64).equals("1010101010101010")) //Only if ack field is valid check sequence number
                        {
                            String acked_seq_no=actual_ack_string.substring(0,32);
                            long ack_seq_no=Long.parseLong(acked_seq_no,2);
                            if(ack_seq_no==(old_seq_no_acked+1))
                            {

                                ack_flag[token].set(true);
                                old_seq_no_acked=ack_seq_no;
                            }
                            else
                            {
                               ack_flag[token].set(false);         
                            }
                        }
                    }
                    //Thread.sleep(1000);
                }
            }

            catch(Exception e)
            {
                System.out.println("ERROR : ");
                //return false;
            }

        }

    }

}

