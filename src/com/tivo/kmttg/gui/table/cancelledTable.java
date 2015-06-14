package com.tivo.kmttg.gui.table;

import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Stack;
import java.util.Vector;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.Task;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableCell;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableView;
import javafx.scene.control.cell.TreeItemPropertyValueFactory;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.util.Callback;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONException;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.gui.table.TableUtil;
import com.tivo.kmttg.gui.TableMap;
import com.tivo.kmttg.gui.gui;
import com.tivo.kmttg.gui.comparator.DateComparator;
import com.tivo.kmttg.gui.comparator.DurationComparator;
import com.tivo.kmttg.gui.sortable.sortableDate;
import com.tivo.kmttg.gui.sortable.sortableDuration;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.rpc.Remote;
import com.tivo.kmttg.rpc.rnpl;
import com.tivo.kmttg.util.debug;
import com.tivo.kmttg.util.log;

public class cancelledTable extends TableMap {
   private String currentTivo = null;
   public TreeTableView<Tabentry> TABLE = null;
   private TreeItem<Tabentry> root = new TreeItem<>(new Tabentry(""));
   public ScrollPane scroll = null;
   public String[] TITLE_cols = {"", "SHOW", "DATE", "CHANNEL", "DUR"};
   public String folderName = null;
   public int folderEntryNum = -1;
   private Hashtable<String,Stack<JSONObject>> folders = null;
   private Vector<JSONObject> sortedOrder = null;
   public Hashtable<String,JSONArray> tivo_data = new Hashtable<String,JSONArray>();
   
   // TableMap overrides
   @Override
   public JSONObject getJson(int row) {
      return GetRowData(row);
   }
   @Override
   public int[] getSelected() {
      return TableUtil.GetSelectedRows(TABLE);
   }
   @Override
   public Boolean isRemote() {
      return true;
   }
   @Override
   public void clear() {
      root.getChildren().clear();
   }
   @Override
   public TreeTableView<?> getTreeTable() {
      return TABLE;
   }
   
   public cancelledTable() {
      TABLE = new TreeTableView<Tabentry>();
      TABLE.setShowRoot(false); // Don't show the empty root node
      
      for (String colName : TITLE_cols) {
         if (colName.length() == 0)
            colName = "IMAGE";
         if (colName.equals("IMAGE")) {
            TreeTableColumn<Tabentry,ImageView> col = new TreeTableColumn<Tabentry,ImageView>("");
            col.setCellValueFactory(new TreeItemPropertyValueFactory<Tabentry,ImageView>(colName));
            col.setCellFactory(new ImageCellFactory());
            TABLE.getColumns().add(col);               
         } else if (colName.equals("DATE")) {
            TreeTableColumn<Tabentry,sortableDate> col = new TreeTableColumn<Tabentry,sortableDate>(colName);
            col.setCellValueFactory(new TreeItemPropertyValueFactory<Tabentry,sortableDate>(colName));
            col.setComparator(new DateComparator()); // Custom column sort
            col.setCellFactory(new RightJustifyColFactory()); // Right justify column text
            TABLE.getColumns().add(col);
         } else if (colName.equals("DUR")) {
            TreeTableColumn<Tabentry,sortableDuration> col = new TreeTableColumn<Tabentry,sortableDuration>(colName);
            col.setCellValueFactory(new TreeItemPropertyValueFactory<Tabentry,sortableDuration>(colName));
            col.setComparator(new DurationComparator());
            TABLE.getColumns().add(col);
         } else {
            // Regular String sort
            TreeTableColumn<Tabentry,String> col = new TreeTableColumn<Tabentry,String>(colName);
            col.setCellValueFactory(new TreeItemPropertyValueFactory<Tabentry,String>(colName));
            TABLE.getColumns().add(col);
         }
      }
      
      scroll = new ScrollPane(TABLE);
      scroll.setFitToHeight(true);
      scroll.setFitToWidth(true);
      
      // Add keyboard listener
      TABLE.setOnKeyPressed(new EventHandler<KeyEvent>() {
         public void handle(KeyEvent e) {
            KeyPressed(e);
         }
      });
      
      // Define selection listener to detect table row selection changes
      TABLE.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<TreeItem<Tabentry>>() {
         @Override
         public void changed(ObservableValue<? extends TreeItem<Tabentry>> obs, TreeItem<Tabentry> oldSelection, TreeItem<Tabentry> newSelection) {
            if (newSelection != null) {
               TABLERowSelected(newSelection.getValue());
            }
         }
      });

      
      // Add right mouse button handler
      TableUtil.AddRightMouseListener(TABLE);
   }

   private class ImageCellFactory implements Callback<TreeTableColumn<Tabentry, ImageView>, TreeTableCell<Tabentry, ImageView>> {
      public TreeTableCell<Tabentry, ImageView> call(TreeTableColumn<Tabentry, ImageView> param) {
         TreeTableCell<Tabentry, ImageView> cell = new TreeTableCell<Tabentry, ImageView>() {
            @Override
            public void updateItem(final ImageView item, boolean empty) {
               super.updateItem(item,  empty);
               if (empty) {
                  setGraphic(null);
               } else {
                  if (item != null)
                     setGraphic(item);
               }
            }
         };
         return cell;
      }
   }   
   
   // Right justify column text (for DATE column)
   private class RightJustifyColFactory implements Callback<TreeTableColumn<Tabentry, sortableDate>, TreeTableCell<Tabentry, sortableDate>> {
      public TreeTableCell<Tabentry, sortableDate> call(TreeTableColumn<Tabentry, sortableDate> tableView) {
         TreeTableCell<Tabentry, sortableDate> cell = new TreeTableCell<Tabentry, sortableDate>() {
            @Override
            public void updateItem(sortableDate entry, boolean empty) {
               super.updateItem(entry, empty);
               if (empty)
                  setText("");
               else {
                  if (entry != null) {
                     setText(entry.toString());
                     setAlignment(Pos.TOP_RIGHT);
                  }
               }
            }
         };
         return cell;
      }
   }
   
   public static class Tabentry {
      ImageView image = new ImageView();
      public String title = "";
      public String channel = "";
      public sortableDate date;
      public sortableDuration duration;
      
      // Root node constructor
      public Tabentry(String s) {
         // Do nothing
      }

      public Tabentry(JSONObject entry) {
         try {
            // If entry is in 1 of todo lists then add special __inTodo__ JSON entry
            config.gui.remote_gui.flagIfInTodo(entry, true);
            String startString=null, endString=null;
            long start=0, end=0;
            if (entry.has("scheduledStartTime")) {
               startString = entry.getString("scheduledStartTime");
               start = TableUtil.getLongDateFromString(startString);
               endString = entry.getString("scheduledEndTime");
               end = TableUtil.getLongDateFromString(endString);
            } else if (entry.has("startTime")) {
               start = TableUtil.getStartTime(entry);
               end = TableUtil.getEndTime(entry);
            }
            title = TableUtil.makeShowTitle(entry);
            channel = TableUtil.makeChannelName(entry);   
            date = new sortableDate(entry, start);
            duration = new sortableDuration(end-start, false);
         } catch (JSONException e) {
            log.error("cancelledTable Tabentry - " + e.getMessage());
         }
      }
      
      public Tabentry(String fName, Stack<JSONObject> folderEntry) {
         image.setImage(gui.Images.get("folder"));
         title = fName;
         date = new sortableDate(fName, folderEntry);
      }
      
      public ImageView getIMAGE() {
         return image;
      }

      public String getSHOW() {
         return title;
      }

      public String getCHANNEL() {
         return channel;
      }

      public sortableDate getDATE() {
         return date;
      }

      public sortableDuration getDUR() {
         return duration;
      }
      
      public String toString() {
         return title;
      }
   }
   
   public JSONObject GetRowData(int row) {
      return TABLE.getTreeItem(row).getValue().getDATE().json;
   }
   
   // Handle keyboard presses
   private void KeyPressed(KeyEvent e) {
      if (e.isControlDown())
         return;
      KeyCode keyCode = e.getCode();
      if (keyCode == KeyCode.I) {
         int[] selected = TableUtil.GetSelectedRows(TABLE);
         if (selected == null || selected.length < 1)
            return;
         JSONObject json = GetRowData(selected[0]);
         if (json != null) {
            config.gui.show_details.update(TABLE, currentTivo, json);
         }
      }
      else if (keyCode == KeyCode.R) {
         config.gui.remote_gui.cancel_tab.record.fire();
      }
      else if (keyCode == KeyCode.E) {
         config.gui.remote_gui.cancel_tab.explain.fire();
      }
      else if (keyCode == KeyCode.J) {
         // Print json of selected row to log window
         int[] selected = TableUtil.GetSelectedRows(TABLE);
         if (selected == null || selected.length < 1)
            return;
         JSONObject json = GetRowData(selected[0]);
         if (json != null)
            rnpl.pprintJSON(json);
      } else if (keyCode == KeyCode.Q) {
         // Web query currently selected entry
         int[] selected = TableUtil.GetSelectedRows(TABLE);
         if (selected == null || selected.length < 1)
            return;
         JSONObject json = GetRowData(selected[0]);
         if (json != null && json.has("title")) {
            try {
               String title = json.getString("title");
               if (json.has("subtitle"))
                  title = title + " - " + json.getString("subtitle");
               TableUtil.webQuery(title);
            } catch (JSONException e1) {
               log.error("KeyPressed Q - " + e1.getMessage());
            }  
         }
      }
   }
   
   // Procedure to mimic expanding folder in row 0
   public void expandFirstFolder() {
      if (root.getChildren().size() > 0) {
         int row = 0;
         sortableDate s = TABLE.getTreeItem(row).getValue().getDATE();
         if (s.folder) {
            folderName = s.folderName;
            folderEntryNum = row;
            Refresh(s.folderData_json);
         }
      }
   }
         
   private void TABLERowSelected(Tabentry entry) {
      // Get column items for selected row 
      sortableDate s = entry.getDATE();
      if (s.folder) {
         // Folder entry - don't display anything
      } else {
         try {
            // Non folder entry so print single entry info
            sortableDuration dur = entry.getDUR();
            String message = TableUtil.makeShowSummary(s, dur);
            String title = "\nWill not record: ";
            if (s.json.has("title"))
               title += s.json.getString("title");
            if (s.json.has("subtitle"))
               title += " - " + s.json.getString("subtitle");
            if (s.json.has("__inTodo__"))
               title += " (to be recorded on " + s.json.getString("__inTodo__") + ")";
            log.warn(title);
            log.print(message);
            
            if (config.gui.show_details.isShowing())
               config.gui.show_details.update(TABLE, currentTivo, s.json);
         } catch (JSONException e) {
            log.error("TABLERowSelected - " + e.getMessage());
            return;
         }
      }
   }

   // Update table to display given entries
   public void AddRows(String tivoName, JSONArray data) {
      try {
         Stack<JSONObject> o = new Stack<JSONObject>();
         JSONObject json;
         long now = new Date().getTime();
         long start;
         Boolean includePast = config.gui.remote_gui.cancel_tab.includeHistory.isSelected();
         for (int i=0; i<data.length(); ++i) {
            json = data.getJSONObject(i);
            if (includePast) {
               // No filter - include all
               o.add(json);
            } else {
               // Filter out past recordings
               start = getStartTime(json);
               if (start >= now)
                  o.add(json);
            }
         }
         // Reset local entries/folders hashes to new entries
         folderize(o); // create folder structure
         Refresh(o);
         TableUtil.autoSizeTableViewColumns(TABLE, true);
         tivo_data.put(tivoName, data);
         currentTivo = tivoName;
         if (config.gui.remote_gui != null)
            config.gui.remote_gui.setTivoName("cancel", tivoName);
      } catch (JSONException e) {
         log.print("AddRows - " + e.getMessage());
      }      
   }
   
   // Refresh table with given given entries
   public void Refresh(Stack<JSONObject> o) {
      if (o == null) {
         if (currentTivo != null)
            AddRows(currentTivo, tivo_data.get(currentTivo));
         return;
      }
      if (TABLE != null) {
         displayFolderStructure();
         root.setExpanded(true);
         TABLE.setRoot(root);
      }
   }
   
   // Update table display to show top level folderized entries
   public void displayFolderStructure() {
      debug.print("");
      clear();
      // Folder based structure
      String name;
      // Add all folders
      for (int i=0; i<sortedOrder.size(); ++i) {
         try {
            name = sortedOrder.get(i).getString("__folderName__");
            AddTABLERow(name + " (" + folders.get(name).size() + ")", folders.get(name));
         } catch (JSONException e) {
            log.print("displayFolderStructure - " + e.getMessage());
         }
      }
   }
   
   private Boolean shouldIgnoreFolder(String folderName) {
      Boolean ignore = false;
      String[] ignoreFolders = {"none", "convertedLiveCache", "expired"};
      for (int i=0; i<ignoreFolders.length; ++i) {
         if (folderName.equals(ignoreFolders[i]))
            ignore = true;
      }
      return ignore;   
   }
   
   // Create data structure to organize entries in folder format
   private void folderize(Stack<JSONObject> entries) {
      debug.print("entries=" + entries);
      folders = new Hashtable<String,Stack<JSONObject>>();
      String name;
      try {
         for (int i=0; i<entries.size(); i++) {
            // Categorize by cancellationReason
            if (entries.get(i).has("cancellationReason"))
               name = entries.get(i).getString("cancellationReason");
            else
               name = "none";
            if ( ! shouldIgnoreFolder(name) ) {
               if ( ! folders.containsKey(name) ) {
                  // Init new stack
                  Stack<JSONObject> stack = new Stack<JSONObject>();
                  folders.put(name, stack);
               }
            folders.get(name).add(entries.get(i));
            }
         }
         
         // Define default sort order for all folder entries
         // Sort by largest start time first
         Comparator<JSONObject> folderSort = new Comparator<JSONObject>() {
            public int compare(JSONObject o1, JSONObject o2) {
               long gmt1 = getStartTime(o1);
               long gmt2 = getStartTime(o2);
               if (gmt1 < gmt2) return 1;
               if (gmt1 > gmt2) return -1;
               return 0;
            }
         };      
         JSONObject entry;
         sortedOrder = new Vector<JSONObject>();
         for (Enumeration<String> e=folders.keys(); e.hasMoreElements();) {
            name = e.nextElement();
            entry = new JSONObject(folders.get(name).get(0));
            entry.put("__folderName__", name);
            sortedOrder.add(entry);
         }
         Collections.sort(sortedOrder, folderSort);
      } catch (JSONException e1) {
         log.error("folderize - " + e1.getMessage());
      }
   }

   // Add a folder entry to table
   public void AddTABLERow(String fName, Stack<JSONObject> folderEntry) {
      TreeItem<Tabentry> item = new TreeItem<>( new Tabentry(fName, folderEntry) );
      for (int i=0; i<folderEntry.size(); ++i) {
         TreeItem<Tabentry> subitem = new TreeItem<>( new Tabentry(folderEntry.get(i)) );
         item.getChildren().add(subitem);
      }
      // Want to resize columns whenever a tree is expanded or collapsed
      item.expandedProperty().addListener(new ChangeListener<Boolean>() {
         @Override
         public void changed(ObservableValue<? extends Boolean> arg0, Boolean arg1, Boolean arg2) {
            TableUtil.autoSizeTableViewColumns(TABLE, true);
         }         
      });
      root.getChildren().add(item);
      // Adjust column widths to data
      TableUtil.autoSizeTableViewColumns(TABLE, true);
   }

   // Look for entry with given folder name and select it
   // (This used when returning back from folder mode to top level mode)
   public void SelectFolder(String folderName) {
      debug.print("folderName=" + folderName);
      for (int i=0; i<root.getChildren().size(); ++i) {
         sortableDate s = root.getChildren().get(i).getValue().getDATE();
         if (s.folder) {
            if (s.folderName.equals(folderName)) {
               TABLE.getSelectionModel().clearSelection();
               TABLE.getSelectionModel().select(i);
               TableUtil.scrollToCenter(TABLE, i);
               return;
            }
         }
      }
   }
      
   private long getStartTime(JSONObject entry) {
      String startString;
      try {
         if (entry.has("scheduledStartTime")) {
            startString = entry.getString("scheduledStartTime");
            return TableUtil.getLongDateFromString(startString);
         } else
            return 0;
      } catch (JSONException e) {
         log.error("getStartTime - " + e.getMessage());
         return 0;
      }
   }
   
   // Schedule a single recording
   public void recordSingle(String tivoName) {
      int[] selected = TableUtil.GetSelectedRows(TABLE);
      if (selected.length > 0) {
         int row;
         JSONArray entries = new JSONArray();
         JSONObject json;
         for (int i=0; i<selected.length; ++i) {
            row = selected[i];
            json = GetRowData(row);
            entries.put(json);
         }
         TableUtil.recordSingleCB(tivoName, entries);
      }
   }
   
   // For show in given row try and obtain and print conflict details to message window
   // This only applies to entries under programSourceConflict folder
   public void getConflictDetails(final String tivoName, final int row) {
     Task<Void> task = new Task<Void>() {
        @Override public Void call() {
            JSONObject json = GetRowData(row);
            try {
               if (json != null && json.getString("cancellationReason").equals("programSourceConflict")) {
                  if (json.has("offerId") && json.has("contentId")) {
                     JSONObject j = new JSONObject();
                     j.put("offerId", json.getString("offerId"));
                     j.put("contentId", json.getString("contentId"));
                     if (json.has("requestedStartPadding"))
                        j.put("startTimePadding", json.getInt("requestedStartPadding"));
                     if (json.has("requestedEndPadding"))
                        j.put("endTimePadding", json.getInt("requestedEndPadding"));
                     // This signifies to check conflicts only, don't subscribe
                     j.put("conflictsOnly", true);
                     Remote r = config.initRemote(tivoName);
                     if (r.success) {
                        JSONObject result = r.Command("Singlerecording", j);
                        if (result != null) {
                           log.print(rnpl.recordingConflicts(result,json));
                        }
                        r.disconnect();
                     }
                  }
               } else {
                  log.warn("Explain button is only relevant for 'programSourceConflict' entries");
               }
            } catch (JSONException e) {
               log.error("getConflictDetails error - " + e.getMessage());
            }
            return null;
         }
      };
      new Thread(task).start();
      
   }

}