import java.io.*;
import java.awt.*;
import java.net.*;
import java.util.*;
import javax.swing.*;
import java.nio.file.*;
import java.awt.event.*;
import java.util.concurrent.*;
// Joe T. Schwarz (C)
public class iForum extends JFrame implements ActionListener {
	private static final long serialVersionUID = 31L;
  private ExecutorService pool = Executors.newFixedThreadPool(2048);
  private String uList = "iForumUserList.txt";
	private volatile boolean closed = false, locked = false;
	private ConcurrentHashMap<String, String> vp;
  private DefaultComboBoxModel<String> model;
  private ServerSocket cServer;
	private JTextArea taLog;
	private int maxCap = 100, port;
  private JComboBox<String> jcb;
  private JTextField line;
  private java.util.List<String> chatters = Collections.synchronizedList(new ArrayList<>(200));
  private java.util.List<OutputStream> out = Collections.synchronizedList(new ArrayList<>(200));
  //
	public iForum(String title, String sPort) {
		super(title);
    vp = new ConcurrentHashMap<>(); // user list
		try {
				port = Integer.parseInt(sPort);
        File file = new File(uList);
        if (file.exists()) {
          String[] sp = (new String(Files.readAllBytes(file.toPath()))).replace("\r", "").split("\n");
          for (int i = 0; i < sp.length; i += 2) vp.put(sp[i], sp[i+1]);
        } else { // create uList
          FileOutputStream fou = new FileOutputStream(uList);
          fou.close();
        }
		} catch (Exception e) { 
			JOptionPane.showMessageDialog( null, "Invalid Port "+sPort); 
			System.exit(0);
		}
    pool.execute(() -> {
			try {
				cServer = new ServerSocket(port);				
				while (!closed) pool.execute(new Newbie(cServer.accept()));
			} catch (Exception e) {
        if (!closed) e.printStackTrace();
        System.exit(0);
      }		
		});
		//
    line = new JTextField(35);
    line.addActionListener(this);
    line.setBackground(Color.green);
    Vector<String> vec = new Vector<>();
    vec.add("ALL");
    model = new DefaultComboBoxModel<>(vec);
    jcb = new JComboBox<>(model);
    jcb.setPreferredSize(new Dimension(120, 25));
    jcb.addActionListener(this);
    
    JPanel forum = new JPanel();
    forum.add(jcb);
    forum.add(line);
    jcb.addActionListener(this); 
    //
		JPanel pnl = new JPanel();
		pnl.setBackground(Color.pink);		
		JButton butGO = new JButton("EXIT");
		butGO.setForeground(Color.black);
		butGO.setFocusable(false);
		butGO.setFont(new Font("Times",Font.BOLD, 11));
		butGO.addActionListener(this);
		pnl.add(butGO);

		GridBagLayout gbs = new GridBagLayout();
		pnl.setLayout(new GridBagLayout());
		GridBagConstraints cCenter = new GridBagConstraints();
		cCenter.anchor  = GridBagConstraints.CENTER;
		gbs.setConstraints(pnl, cCenter);
		
		taLog = new JTextArea("iForum is open...\n");
		taLog.setEditable(false);
		JPanel logPanel = new JPanel();
		taLog.setBackground(Color.lightGray);
		JScrollPane jTextArea = new JScrollPane(taLog); 
		logPanel.setBorder(BorderFactory.createTitledBorder("Joe's iForum's Protocol"));
		taLog.setWrapStyleWord(true);
		jTextArea.setAutoscrolls(true);
		taLog.setLineWrap(true);

		logPanel.setLayout(new BorderLayout());
		logPanel.add(jTextArea, BorderLayout.CENTER);
        
		add("North", forum);
		add("Center",logPanel);
		add("South",pnl);
		
		setSize(550, 400);
		setVisible(true);
		addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent we) {
				actionExit();
			}
		});
	}
  //----------------------------------------------------------------------
  private class Newbie implements Runnable {
   	public Newbie(Socket me) {
   		this.me = me;
   	}
		private InputStream meI;
		private OutputStream meO;
   	private Socket me;
   	private String id;
    private int mIdx;
   	public void run( ) {
			try {
				// create an anonymous ID
				id = "p"+String.format("%08X", (System.nanoTime() & 0xFFFFFFFF));
        model.addElement(id);
				meO = me.getOutputStream();
				meI = me.getInputStream();
				if (!verifyMe( )) return;
				// is a valid member
				chatters.add(id);
				out.add(meO);
				//
				updateList( );			
				synchronized(taLog) {
					taLog.append(id+" joins iForum...\n");
				}
				me.setTcpNoDelay(true);		
				mIdx = chatters.indexOf(id);
        byte[] bb = new byte[2048];
				String iTalk = "<"+id+"> ";
				while (true) {
					int b = meI.read(bb);
					if (b == -1 || bb[0] == (byte)0x02) {
						closeMe( );
             // I quit...
						someoneQuit(mIdx);
						return;
					}
					// <id>message OR <id>#message
					if (bb[0] == (byte) '<') for (int i = 1; i < b; ++i) if (bb[i] == '>') {
						String who = new String(bb, 1, i-1);
            String txt = new String(bb, ++i, b-i);
						if (who.charAt(0) == 'A') { // for the public
              synchronized(taLog) {
                taLog.append(iTalk+"to everyone: "+txt+"\n");
              }
							for (int j = 0, s = chatters.size(); j < s; ++j)
							if (!id.equals(chatters.get(j))) {
								try {
									(out.get(j)).write((iTalk+txt).getBytes());
								} catch (Exception w) {
                   // someone quits
									someoneQuit(j);
								}
							}
						} else if (!who.equals(id)){
							int idx = chatters.indexOf(who);
              synchronized(taLog) {
                taLog.append(iTalk+"to <"+who+"> "+txt+"\n");
              }
							try {
								(out.get(idx)).write((iTalk+txt).getBytes());
							} catch (Exception w) {
                 // someone quits
								someoneQuit(idx);
							}
						}
						break;
					}
				}
			} catch (Exception e) { }
			try {
				closeMe( );
         // I quit
				someoneQuit(mIdx);
			} catch (Exception x) { }
		}
   	private synchronized void updateList( ) {
   		int s = chatters.size( );
			String list = new String((char)0x01+"Whom?!ALL!");
			for (int i = 0; i < s; ++i) {
				list += (chatters.get(i)+"!");
			}
   		byte[] bb = list.getBytes( );
   		for (int i = 0; i < s; ++i) {
   			OutputStream o = out.get(i);
				try {
					o.write(bb);
					o.flush( );
				} catch (Exception x) { }
   		}
   		return;
   	}
		private void closeMe( ) throws Exception {
			meI.close( );
			me.close( );
		}
		private synchronized void someoneQuit(int ix) throws Exception {
			(out.get(ix)).close( );
      String id = chatters.get(ix);
			synchronized (this) {
				taLog.append(id+" quitted.\n");
        model.removeElement(id);
				chatters.remove(ix);
				out.remove(ix);
			}
			updateList( );
		}
   	private boolean verifyMe( ) {
   		try {
        byte[] bb = new byte[512];
				int n = meI.read(bb);
				String els[] = (new String(bb, 0, n)).trim().split("\n");
        if ("register".equals(els[0])) {
          try {
            while(locked) { // is locked?
              TimeUnit.MILLISECONDS.sleep(100);
            }
            locked = true; // lock it
            File file = new File(uList);
            if (file.exists()) {
              java.util.List<String> users = Files.readAllLines(file.toPath());
              for (int i = 0, mx = users.size(); i < mx; i += 2) 
              if (users.get(i).equals(els[1])) { // existed ?
                locked = false; // unlocked
                meO.write((Authenticate.decrypt(els[1])+" exists.").getBytes());
                return false;
              }
            }
            // and register new chatter's ID + PW (both in encrypted format)
            FileOutputStream fou = new FileOutputStream(uList, true); // extended
            fou.write((els[1]+"\n"+els[2]+"\n").getBytes());
            fou.flush();
            fou.close();
            locked = false;
            // cached this ID/PW
            vp.put(els[1], els[2]);
            // send this id to chatter
            meO.write(("\n"+id).getBytes());
            return true;
          } catch (Exception ex) { }
          locked = false;
        } else { // it's a login
          if (vp.get(els[0]).equals(els[1])) {
            meO.write(("\n"+id).getBytes());
            return true;
          }
        }
        // unknown ID/PW
			} catch (Exception e) { }
   		synchronized(taLog) {
   			taLog.append("An unknown intruder was kicked out...\n");
   		}
   		try {
        meO.write('X'); 
        meO.flush();
   			meO.close();
   			closeMe( ); // throw me out
   		} catch (Exception o) { }
      return false;
   	}
  }
  //
	public void actionPerformed(ActionEvent a) {
    if (a.getActionCommand().equals("EXIT")) actionExit();
	  else {
        int mx = chatters.size();
        String s = line.getText().trim();
        String user = (String) jcb.getSelectedItem();
        if (s.length() > 0 && mx > 0) try {
            if ("ALL".equals(user)) {
              byte[] buf = ("@all: "+s).getBytes();
              for (int i = 0; i < mx; ++i) out.get(i).write(buf);
              synchronized(taLog) {
                taLog.append("iForum to everyone: "+s+"\n");
              }
            } else {
              out.get(chatters.indexOf(user)).write(("iForum to <"+user+">: "+s).getBytes());
              synchronized(taLog) {
                taLog.append("iForum to <"+user+">: "+s+"\n");
              }
            }
            jcb.removeActionListener(this);
            model.setSelectedItem("ALL");
            jcb.addActionListener(this);
            line.setText("");
        } catch (Exception ex) {
            synchronized(taLog) {
              taLog.append("iForum is unable to send\""+s+"\" to <"+user+">\n");
            }
        }
    }
  }
  //----------------------------------------------------------------------
  public void actionExit( ) {
   	closed = true; //shutdown the server
   	if (cServer != null) try {
      cServer.close();
   		Thread.sleep(50);
   	} catch (Exception e) { }
    pool.shutdownNow();
    System.exit(0);
  }
  //
  public static void main(String... args) throws Exception {
    UIManager.setLookAndFeel("com.sun.java.swing.plaf.nimbus.NimbusLookAndFeel");
		if (args.length == 1) {
      new iForum("Joe's iForum", args[0]);
		} else {
			JOptionPane.showMessageDialog( null, "Usage: java iForum Port"); 
			System.exit(0);
		}
	}
}
