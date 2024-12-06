import java.io.*;
import java.awt.*;
import java.net.*;
import java.util.*;
import javax.swing.*;
import java.nio.file.*;
import java.awt.event.*;
import java.util.concurrent.*;
// Joe T. Schwarz (C)
public class UDPForum extends JFrame implements ActionListener {

    private JTextField line;
    private JTextArea taLog;
    private UDPForum me = this;
    private JComboBox<String> jcb;
    private volatile boolean closed = false, locked = false;
    private DefaultComboBoxModel<String> model;
    private String ufile = "udpForumUserList.txt";
    private ExecutorService pool = Executors.newFixedThreadPool(2048);
    // JavaSWING...the chores
    public UDPForum(String title, final int port) {
        super(title);
        ipList = new ConcurrentHashMap<>();
        cList = new ConcurrentHashMap<>();
        pool.execute(() -> {
          try {
              udpServer = new DatagramSocket(port);
              while (!closed) {
                DatagramPacket pkt = new DatagramPacket(new byte[65001], 65001);
                udpServer.receive(pkt);
                pool.execute(new UDPuChatter(pkt));
             }
           } catch (Exception e) {
             if (!closed) e.printStackTrace();
           }           
           shutdown();
        });
        JPanel pnl = new JPanel();
        pnl.setBackground(Color.pink);           
        JButton butGO = new JButton("EXIT");
        butGO.setForeground(Color.black);
        butGO.setFocusable(false);
        butGO.setFont(new Font("Times",Font.BOLD, 11));
        butGO.addActionListener(a -> {
          shutdown();
        });
        pnl.add(butGO);

        JPanel lPanel = new JPanel();
        line = new JTextField(35);
        line.setEditable(true);
        line.setBackground(Color.green);
        line.addActionListener(this);   
        lPanel.add( new JLabel("To "));
        Vector<String> vec = new Vector<>();
        vec.add("ALL");
        //
        model = new DefaultComboBoxModel<>(vec);
        jcb = new JComboBox<>(model);
        jcb.setPreferredSize(new Dimension(100, 25));
        jcb.addActionListener(this); 
        lPanel.add(jcb);
        lPanel.add(line);
          
        taLog = new JTextArea(16, 42);
        taLog.setEditable(false);
        JPanel logPanel = new JPanel();
        taLog.setBackground(Color.lightGray);
        JScrollPane jTextArea = new JScrollPane(taLog);
        logPanel.setBorder(BorderFactory.createTitledBorder("Joe's UDPForum's Protocol"));
        taLog.setWrapStyleWord(true);
        jTextArea.setAutoscrolls(true);
        taLog.setLineWrap(true);
        logPanel.add(jTextArea);
      
        add("North",lPanel);
        add("Center",logPanel);
        add("South",pnl);
          
        pack();
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent we){
              if (!closed) shutdown();
            }
        });
        setVisible(true);
    }
    private DatagramSocket udpServer;
    private ConcurrentHashMap<String, String> ipList;
    private ConcurrentHashMap<String, uChatter> cList;
    private java.util.List<String> chatters = Collections.synchronizedList(new ArrayList<>(200));
    // This is the UDPForum's slaves
    // UDPuChatter waits for a task, does it and dies
    private class UDPuChatter implements Runnable {
        public UDPuChatter(DatagramPacket pkt) {
            this.pkt = pkt;
        }
        private DatagramPacket pkt;
        public void run( ) {
           try {
               byte buf[];
               int port = pkt.getPort();
               InetAddress ia = pkt.getAddress();
               uChatter uc = null;
               String id = null;
               DatagramSocket ds;
               String ip_port = ia.toString()+":"+port;
               String data = new String(pkt.getData(), 0, pkt.getLength());
               String els[] = data.split("!");
               if (els.length == 3 && ("register".equals(els[0]) || "login".equals(els[0]))) {
                 id = "u"+String.format("%08X", (int)(System.nanoTime() & 0xFFFFFFFF));
                 String uID = Authenticate.decrypt(els[1]);
                 uc = new uChatter(ia, port);
                 ds = new DatagramSocket();
                 //
                 while(locked) { // is locked?
                   TimeUnit.MILLISECONDS.sleep(100);
                 }
                 locked = true; // lock it
                 File file = new File(ufile);
                 if ("register".equals(els[0])) {
                   if (file.exists()) {
                     java.util.List<String> users = Files.readAllLines(file.toPath());
                     for (int i = 0, mx = users.size(); i < mx; i += 2) 
                     if (users.get(i).equals(els[1])) { // existed ?
                       locked = false; // unlocked
                       buf = (uID+" exists already.").getBytes();
                       ds.send(new DatagramPacket(buf, buf.length, ia, port));
                       return;
                     }
                   }
                   uc.id = id; // set ID
                   // and save new chatter's ID + PW (both in encrypted format)
                   FileOutputStream fou = new FileOutputStream(ufile, true); // extended
                   fou.write((els[1]+"\n"+els[2]+"\n").getBytes());
                   fou.flush();
                   fou.close();
                } else {
                  if (file.exists()) {
                    int i = 0;
                    String[] sp = (new String(Files.readAllBytes(file.toPath()))).replace("\r", "").split("\n");
                    for (; i < sp.length; i += 2) if (sp[i].equals(els[1]) && sp[i+1].equals(els[2])) break;
                    if (i < sp.length) uc.id = id;
                    else {
                      locked = false;
                      buf = ("inavlid ID or Password"+uID).getBytes();
                      ds.send(new DatagramPacket(buf, buf.length, ia, port));
                      return;
                    }
                  } else {
                    locked = false;
                    buf = (uID+" is unregistered.").getBytes();
                    ds.send(new DatagramPacket(buf, buf.length, ia, port));
                    return;
                 }
               }
               locked = false;
               synchronized(this) {
                  chatters.add(id);
                  cList.put(id, uc);
                  model.addElement(id);
                  ipList.put(ip_port, id);
               }
               ds = new DatagramSocket();
               String hello = "Welcome "+uID+" with ID: "+id;
               buf = (hello+userList()).getBytes();
               for (String chatter:chatters) {
                  uc = cList.get(chatter);
                  ds = new DatagramSocket();
                  ds.send(new DatagramPacket(buf, buf.length, uc.ip, uc.port));
               }
               synchronized(taLog) {
                 taLog.append(hello+"\n");
               }
               return;
             }
             id = ipList.get(ip_port);
             if (id == null) {
               ds = new DatagramSocket();
               buf = ("ID is null ???").getBytes();
               ds.send(new DatagramPacket(buf, buf.length, ia, port));
               return;
             } else uc = cList.get(id);
             if (data.startsWith("Bye ")) {
               synchronized(this) {
                 ipList.remove(ip_port);
                 model.removeElement(id);
                 chatters.remove(id);
                 cList.remove(id);
               }
               // send updated userList
               buf = userList().getBytes(); 
               for (String chatter:chatters) {
                 uc = cList.get(chatter);
                 ds = new DatagramSocket();
                 ds.send(new DatagramPacket(buf, buf.length, uc.ip, uc.port));
               }
               return;
             }
             int p = data.indexOf("!");
             String who = data.substring(0, p);
             data = data.replace(who+"!", "");
             if ("ALL".equals(who)) {
                 synchronized(taLog) {
                   taLog.append("To everyone: "+data+"\n");
                 }
                 if (chatters.size() > 0) {
                   buf = ("@everyone: "+data).getBytes();
                   for (String chatter:chatters) {
                     uc = cList.get(chatter);
                     ds = new DatagramSocket();
                     ds.send(new DatagramPacket(buf, buf.length, uc.ip, uc.port));
                   }
                 }
               } else {
                 synchronized(taLog) {
                   taLog.append("From "+id+" to "+who+": "+data+"\n");
                 }
                 uc = cList.get(who);
                 buf = ("<"+id+"> "+data).getBytes();
                 ds = new DatagramSocket();
                 ds.send(new DatagramPacket(buf, buf.length, uc.ip, uc.port));
               }
            } catch (Exception e) {
               //e.printStackTrace();
            }
            locked = false;
        }
    }
    //
    private String userList() {
      StringBuilder sb = new StringBuilder("<>ALL");
      if (chatters.size() > 0) for (String chatter:chatters) {
        uChatter uc = cList.get(chatter);
        if (uc != null) sb.append("!"+uc.id);
      }
      return sb.toString();
    }
    // reply to newcomer
    public void actionPerformed(ActionEvent ev) {
        try {
            String s = line.getText().trim();
            String user = (String) jcb.getSelectedItem();
            if ("ALL".equals(user)) {
              if (chatters.size() > 0) {
                byte[] buf = ("@all: "+s).getBytes();
                for (String chatter:chatters) {
                  uChatter uc = cList.get(chatter);
                  DatagramSocket ds = new DatagramSocket();
                  ds.send(new DatagramPacket(buf, buf.length, uc.ip, uc.port));
                }
              }
              synchronized(taLog) {
                taLog.append("UDPForum to everyone: "+s+"\n");
              }
            } else {
              uChatter uc = cList.get(user);
              synchronized(taLog) {
                taLog.append("To "+user+": "+s+"\n");
              }
              byte buf[] = ("UDPForum to "+user+": "+s+"\n").getBytes();
              DatagramSocket ds = new DatagramSocket();
              ds.send(new DatagramPacket(buf, buf.length, uc.ip, uc.port));
            }
            jcb.removeActionListener(me);
            jcb.setSelectedIndex(0);
            jcb.addActionListener(me);
            line.setText("");
        } catch (Exception e) { }
    }
    //----------------------------------------------------------------------
    public void shutdown( ) {
        closed = true; //shutdown the server
        if (chatters.size() > 0) try {
          byte buf[] = "To everyone: UDPForum is closed!".getBytes();
          for (String chatter:chatters) {
            uChatter u = cList.get(chatter);
            DatagramSocket ds = new DatagramSocket();
            ds.send(new DatagramPacket(buf, buf.length, u.ip, u.port));
          }
          TimeUnit.MILLISECONDS.sleep(500);
        } catch (Exception ex) { }
        if (udpServer != null) try {
            udpServer.close( );
            TimeUnit.MILLISECONDS.sleep(100);
        } catch (Exception e) { }
        pool.shutdownNow();
        System.exit(0);
    }

    public static void main(String[] a) {
      if (a.length != 1) {
        JOptionPane.showMessageDialog( null, "Usage: java UDPForum HostName/IP:Port"); 
        System.exit(0);
      } else try {
            UIManager.setLookAndFeel("com.sun.java.swing.plaf.nimbus.NimbusLookAndFeel");
            int port = Integer.parseInt(a[0]);
            UDPForum srv = new UDPForum("Joe's UDPForum for UDPClients", port);
      } catch (Exception e) {
           JOptionPane.showMessageDialog( null, "Can't start NimbusLookAndFeel or invalid port:"+a[0]);
           System.exit(0);
      }
    }
}
