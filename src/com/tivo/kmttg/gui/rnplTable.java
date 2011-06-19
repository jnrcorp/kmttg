package com.tivo.kmttg.gui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Comparator;
import java.util.Date;
import java.util.Hashtable;

import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableColumnModel;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;

import org.jdesktop.swingx.JXTable;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONException;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.rpc.Remote;
import com.tivo.kmttg.util.debug;
import com.tivo.kmttg.util.log;

public class rnplTable {
   private String[] TITLE_cols = {"SHOW", "DATE", "CHANNEL", "DUR", "SIZE"};
   public JXTable TABLE = null;
   public Hashtable<String,JSONArray> tivo_data = new Hashtable<String,JSONArray>();
   public JScrollPane scroll = null;
   private JDialog dialog = null;

   rnplTable(JDialog dialog) {
      this.dialog = dialog;
      Object[][] data = {};
      TABLE = new JXTable(data, TITLE_cols);
      TABLE.setModel(new MyTableModel(data, TITLE_cols));
      scroll = new JScrollPane(TABLE);
      // Add listener for click handling (for folder entries)
      TABLE.addMouseListener(
         new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
               MouseClicked(e);
            }
         }
      );
      // Add keyboard listener
      TABLE.addKeyListener(
         new KeyAdapter() {
            public void keyReleased(KeyEvent e) {
               KeyPressed(e);
            }
         }
      );

      
      // Change color & font
      TableColumn tm;
      tm = TABLE.getColumnModel().getColumn(0);
      tm.setCellRenderer(new ColorColumnRenderer(config.tableBkgndLight, config.tableFont));
      ((JLabel) tm.getCellRenderer()).setHorizontalAlignment(JLabel.LEFT);
      tm = TABLE.getColumnModel().getColumn(1);
      tm.setCellRenderer(new ColorColumnRenderer(config.tableBkgndDarker, config.tableFont));
      ((JLabel) tm.getCellRenderer()).setHorizontalAlignment(JLabel.RIGHT);
      tm = TABLE.getColumnModel().getColumn(2);
      tm.setCellRenderer(new ColorColumnRenderer(config.tableBkgndLight, config.tableFont));
      ((JLabel) tm.getCellRenderer()).setHorizontalAlignment(JLabel.LEFT);
      tm = TABLE.getColumnModel().getColumn(3);
      tm.setCellRenderer(new ColorColumnRenderer(config.tableBkgndDarker, config.tableFont));
      ((JLabel) tm.getCellRenderer()).setHorizontalAlignment(JLabel.LEFT);
      tm = TABLE.getColumnModel().getColumn(4);
      tm.setCellRenderer(new ColorColumnRenderer(config.tableBkgndLight, config.tableFont));
      ((JLabel) tm.getCellRenderer()).setHorizontalAlignment(JLabel.RIGHT);
   }
   
   
   // Define custom column sorting routines
   Comparator<Object> sortableComparator = new Comparator<Object>() {
      public int compare(Object o1, Object o2) {
         if (o1 instanceof sortableDate && o2 instanceof sortableDate) {
            sortableDate s1 = (sortableDate)o1;
            sortableDate s2 = (sortableDate)o2;
            long l1 = Long.parseLong(s1.sortable);
            long l2 = Long.parseLong(s2.sortable);
            if (l1 > l2) return 1;
            if (l1 < l2) return -1;
            return 0;
         }
         if (o1 instanceof sortableDuration && o2 instanceof sortableDuration) {
            sortableDuration s1 = (sortableDuration)o1;
            sortableDuration s2 = (sortableDuration)o2;
            if (s1.sortable > s2.sortable) return 1;
            if (s1.sortable < s2.sortable) return -1;
            return 0;
         }
         return 0;
      }
   };

   /**
    * Applied background color to single column of a JTable
    * in order to distinguish it apart from other columns.
    */ 
    class ColorColumnRenderer extends DefaultTableCellRenderer 
    {
       private static final long serialVersionUID = 1L;
       Color bkgndColor;
       Font font;
       
       public ColorColumnRenderer(Color bkgnd, Font font) {
          super();
          // Center text in cells
          setHorizontalAlignment(CENTER);
          bkgndColor = bkgnd;
          this.font = font;
       }
       
       public Component getTableCellRendererComponent
           (JTable table, Object value, boolean isSelected,
            boolean hasFocus, int row, int column) 
       {
          Component cell = super.getTableCellRendererComponent
             (table, value, isSelected, hasFocus, row, column);
     
          if (bkgndColor != null && ! isSelected)
             cell.setBackground( bkgndColor );
          
          cell.setFont(config.tableFont);
         
          return cell;
       }
    } 
    
    
    // Override some default table model actions
    class MyTableModel extends DefaultTableModel {
       private static final long serialVersionUID = 1L;

       public MyTableModel(Object[][] data, Object[] columnNames) {
          super(data, columnNames);
       }
       
       @SuppressWarnings("unchecked")
       // This is used to define columns as specific classes
       public Class getColumnClass(int col) {
          if (col == 2) {
             return sortableDate.class;
          }
          if (col == 4) {
             return sortableDuration.class;
          }
          if (col == 5) {
             return sortableDouble.class;
          }
          return Object.class;
       } 
       
       // Set all cells uneditable
       public boolean isCellEditable(int row, int column) {        
          return false;
       }
    }

    // Pack all table columns to fit widest cell element
    public void packColumns(JXTable table, int margin) {
       debug.print("table=" + table + " margin=" + margin);
       //if (config.tableColAutoSize == 1) {
          for (int c=0; c<table.getColumnCount(); c++) {
              packColumn(table, c, 2);
          }
       //}
    }

    // Sets the preferred width of the visible column specified by vColIndex. The column
    // will be just wide enough to show the column head and the widest cell in the column.
    // margin pixels are added to the left and right
    // (resulting in an additional width of 2*margin pixels).
    public void packColumn(JXTable table, int vColIndex, int margin) {
       debug.print("table=" + table + " vColIndex=" + vColIndex + " margin=" + margin);
        DefaultTableColumnModel colModel = (DefaultTableColumnModel)table.getColumnModel();
        TableColumn col = colModel.getColumn(vColIndex);
        int width = 0;
    
        // Get width of column header
        TableCellRenderer renderer = col.getHeaderRenderer();
        if (renderer == null) {
            renderer = table.getTableHeader().getDefaultRenderer();
        }
        Component comp = renderer.getTableCellRendererComponent(
            table, col.getHeaderValue(), false, false, 0, 0);
        width = comp.getPreferredSize().width;
    
        // Get maximum width of column data
        for (int r=0; r<table.getRowCount(); r++) {
            renderer = table.getCellRenderer(r, vColIndex);
            comp = renderer.getTableCellRendererComponent(
                table, table.getValueAt(r, vColIndex), false, false, r, vColIndex);
            width = Math.max(width, comp.getPreferredSize().width);
        }
    
        // Add margin
        width += 2*margin;
               
        // Set the width
        col.setPreferredWidth(width);
        
        // Adjust SHOW column to fit available space
        int last = getColumnIndex("SHOW");
        if (vColIndex == last) {
           int twidth = table.getPreferredSize().width;
           int awidth = dialog.getWidth();
           int offset = 3*scroll.getVerticalScrollBar().getPreferredSize().width+2*margin;
           if ((awidth-offset) > twidth) {
              width += awidth-offset-twidth;
              col.setPreferredWidth(width);
           }
        }
    }
    
    public int getColumnIndex(String name) {
       String cname;
       for (int i=0; i<TABLE.getColumnCount(); i++) {
          cname = (String)TABLE.getColumnModel().getColumn(i).getHeaderValue();
          if (cname.equals(name)) return i;
       }
       return -1;
    }
    
    public void clear() {
       debug.print("");
       DefaultTableModel model = (DefaultTableModel)TABLE.getModel(); 
       model.setNumRows(0);
    }

    public void AddRows(String tivoName, JSONArray data) {
       try {
          for (int i=0; i<data.length(); ++i) {
             AddRow(data.getJSONObject(i));
             //System.out.println(data.getJSONObject(i));
          }
          tivo_data.put(tivoName, data);
          packColumns(TABLE,2);
       } catch (JSONException e) {
          log.error("rnplTable AddRows - " + e.getMessage());
       }
    }
    
    private void AddRow(JSONObject data) {
       debug.print("data=" + data);
       //"SHOW", "DATE", "CHANNEL", "DUR", "SIZE"
       try {
          JSONObject o = data.getJSONArray("recording").getJSONObject(0);
          JSONObject o2 = new JSONObject();
          Object[] info = new Object[TITLE_cols.length];
          String startString;
          if (o.has("scheduledStartTime"))
             startString = o.getString("scheduledStartTime");
          else
             startString = o.getString("actualStartTime");
          long start = getLongDateFromString(startString);
          long duration = 0;
          if (o.has("duration"))
             duration = o.getLong("duration");
          String title = " ";
          if (o.has("title"))
             title += o.getString("title");
          if (o.has("subtitle"))
             title += " - " + o.getString("subtitle");
          String channel = " ";
          if (o.has("channel")) {
             o2 = o.getJSONObject("channel");
             if (o2.has("channelNumber"))
                channel += o2.getString("channelNumber");
             if (o2.has("callSign"))
                channel += "=" + o2.getString("callSign");
          }
          Long size = (long)0;
          if (o.has("size"))
             size = o.getLong("size");
          info[0] = title;          
          info[1] = new sortableDate(o, start);
          info[2] = channel;
          info[3] = new sortableDuration(duration*1000, false);
          info[4] = new sortableSize(size);
          AddRow(TABLE, info);       
       } catch (JSONException e) {
          log.error("rnplTable AddRow - " + e.getMessage());
       }
    }
    
    private void AddRow(JTable table, Object[] data) {
       debug.print("table=" + table + " data=" + data);
       DefaultTableModel dm = (DefaultTableModel)table.getModel();
       dm.addRow(data);
    }
    
    public int[] GetSelectedRows() {
       debug.print("");
       int[] rows = TABLE.getSelectedRows();
       if (rows.length <= 0)
          log.error("No rows selected");
       return rows;
    }
    
    // Mouse event handler
    // This will display folder entries in table if folder entry single-clicked
    private void MouseClicked(MouseEvent e) {
       if( e.getClickCount() == 1 ) {
          int row = TABLE.rowAtPoint(e.getPoint());
          sortableDate s = (sortableDate)TABLE.getValueAt(row,getColumnIndex("DATE"));
          log.print(s.json.toString());
       }
    }
    
    // Handle delete keyboard presses
    private void KeyPressed(KeyEvent e) {
       int keyCode = e.getKeyCode();
       if (keyCode == KeyEvent.VK_SPACE || keyCode == KeyEvent.VK_DELETE) {
          // Play selected show if space key pressed
          int[] selected = GetSelectedRows();
          if (selected != null && selected.length > 0) {
             int row = selected[0];
             String recordingId;
             sortableDate s = (sortableDate)TABLE.getValueAt(row,getColumnIndex("DATE"));
             if (s.json.has("recordingId")) {
                String tivoName = config.gui.remote_gui.getTivoName("rnpl");
                Remote r = new Remote(config.TIVOS.get(tivoName), config.MAK);
                if (r.success) {
                   JSONObject json = new JSONObject();
                   try {
                      recordingId = s.json.getString("recordingId");
                      json.put("id", recordingId);
                      String title = "";
                      if (s.json.has("title"))
                         title += s.json.getString("title");
                      if (s.json.has("subtitle"))
                         title += s.json.getString("subtitle");
                      if (title.length() == 0)
                         title = recordingId;
                      if (keyCode == KeyEvent.VK_SPACE) {
                         log.warn("Remote control playing show on '" + tivoName + "': " + title);
                         r.Key("playback", json);
                      }
                      if (keyCode == KeyEvent.VK_DELETE) {
                         log.warn("Remote control deleting show on '" + tivoName + "': " + title);
                         JSONObject response = r.Key("delete", json);
                         if (response != null &&
                               response.has("type") &&
                               response.getString("type").equals("success")) {
                            // Remove table entry
                            DefaultTableModel dm = (DefaultTableModel)TABLE.getModel();
                            dm.removeRow(row);
                            // Remove entry from cache
                            for (int i=0; i<tivo_data.get(tivoName).length(); ++i) {
                               json = tivo_data.get(tivoName).getJSONObject(i).getJSONArray("recording").getJSONObject(0);
                               //log.warn(json.getString("recordingId") + "equals" + recordingId);
                               if (json.getString("recordingId").equals(recordingId))
                                  tivo_data.get(tivoName).remove(i);
                            }
                         }
                      }
                   } catch (JSONException e1) {
                      log.error("rnplTable KeyPress - " + e1.getMessage());
                   }
                   r.disconnect();
                }
             }
          }
       } else {
          // Pass along keyboard action
          e.consume();
       }
    }

    private long getLongDateFromString(String date) {
       try {
          SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss zzz");
          Date d = format.parse(date + " GMT");
          return d.getTime();
       } catch (ParseException e) {
         log.error("rnplTable getLongDate - " + e.getMessage());
         return 0;
       }
    }

}
