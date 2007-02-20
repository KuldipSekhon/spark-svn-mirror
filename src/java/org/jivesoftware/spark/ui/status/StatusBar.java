/**
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Lesser Public License (LGPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.spark.ui.status;

import org.jivesoftware.resource.Default;
import org.jivesoftware.resource.Res;
import org.jivesoftware.resource.SparkRes;
import org.jivesoftware.smack.packet.PacketExtension;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smackx.packet.VCard;
import org.jivesoftware.spark.PresenceManager;
import org.jivesoftware.spark.SparkManager;
import org.jivesoftware.spark.ui.PresenceListener;
import org.jivesoftware.spark.util.GraphicUtils;
import org.jivesoftware.spark.util.ModelUtil;
import org.jivesoftware.spark.util.SwingWorker;
import org.jivesoftware.spark.util.log.Log;
import org.jivesoftware.sparkimpl.profile.VCardEditor;
import org.jivesoftware.sparkimpl.profile.VCardManager;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.border.Border;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

//TODO: I need to remove the presence logic from this class.
public class StatusBar extends JPanel {
    private List<StatusItem> statusList = new ArrayList<StatusItem>();

    private JLabel imageLabel = new JLabel();
    private JLabel descriptiveLabel = new JLabel();
    private JLabel nicknameLabel = new JLabel();
    private StatusPanel statusPanel = new StatusPanel();

    private Image backgroundImage;

    private Presence currentPresence;

    public StatusBar() {
        setLayout(new GridBagLayout());

        backgroundImage = Default.getImageIcon(Default.TOP_BOTTOM_BACKGROUND_IMAGE).getImage();


        ImageIcon brandedImage = Default.getImageIcon(Default.BRANDED_IMAGE);
        if (brandedImage != null && brandedImage.getIconWidth() > 1) {
            final JLabel brandedLabel = new JLabel(brandedImage);
            add(brandedLabel, new GridBagConstraints(3, 0, 1, 3, 1.0, 0.0, GridBagConstraints.EAST, GridBagConstraints.NONE, new Insets(2, 2, 2, 2), 0, 0));
        }


        add(imageLabel, new GridBagConstraints(0, 0, 1, 4, 0.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(2, 2, 2, 2), 0, 0));

        add(nicknameLabel, new GridBagConstraints(1, 0, 2, 2, 1.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(5, 5, 0, 0), 0, 0));
        add(descriptiveLabel, new GridBagConstraints(1, 2, 1, 1, 0.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(0, 5, 0, 0), 0, 0));

        add(statusPanel, new GridBagConstraints(1, 3, 1, 1, 0.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(0, 2, 0, 0), 0, 0));

        nicknameLabel.setToolTipText(SparkManager.getConnection().getUser());
        nicknameLabel.setFont(new Font("Dialog", Font.BOLD, 12));


        buildStatusItemList();


        setStatus(Res.getString("available"));
        currentPresence = new Presence(Presence.Type.available, Res.getString("available"), 1, Presence.Mode.available);


        setBorder(BorderFactory.createLineBorder(new Color(197, 213, 230), 1));

        SparkManager.getSessionManager().addPresenceListener(new PresenceListener() {
            public void presenceChanged(Presence presence) {
                changeAvailability(presence);
            }
        });

        // Show profile on double click of image label
        imageLabel.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent mouseEvent) {
                if (mouseEvent.getClickCount() == 1) {
                    VCardManager vcardManager = SparkManager.getVCardManager();
                    VCardEditor editor = new VCardEditor();
                    editor.editProfile(vcardManager.getVCard(), SparkManager.getWorkspace());
                }
            }

            public void mouseEntered(MouseEvent e) {
                imageLabel.setCursor(GraphicUtils.HAND_CURSOR);
            }

            public void mouseExited(MouseEvent e) {
                imageLabel.setCursor(GraphicUtils.DEFAULT_CURSOR);
            }
        });
    }

    public void setAvatar(Icon icon) {
        imageLabel.setIcon(icon);
        imageLabel.setBorder(null);
        invalidate();
        validateTree();
    }

    public void setNickname(String nickname) {
        nicknameLabel.setText(nickname);
    }

    /**
     * Sets the current status text in the Status Manager.
     *
     * @param status the status to set.
     */
    public void setStatus(String status) {
        statusPanel.setStatus(status);
    }

    public void showPopup(MouseEvent e) {
        final JPopupMenu popup = new JPopupMenu();

        List custom = CustomMessages.load();
        if (custom == null) {
            custom = new ArrayList();
        }

        // Build menu from StatusList
        for (final StatusItem statusItem : statusList) {
            final Action statusAction = new AbstractAction() {
                public void actionPerformed(ActionEvent actionEvent) {
                    final String text = statusItem.getText();
                    final StatusItem si = getStatusItem(text);
                    if (si == null) {
                        // Custom status
                        Log.error("Unable to find status item for status - " + text);
                        return;
                    }

                    SwingWorker worker = new SwingWorker() {
                        public Object construct() {
                            SparkManager.getSessionManager().changePresence(si.getPresence());
                            return "ok";
                        }

                        public void finished() {
                            setStatus(text);
                        }
                    };
                    worker.start();
                }
            };

            statusAction.putValue(Action.NAME, statusItem.getText());
            statusAction.putValue(Action.SMALL_ICON, statusItem.getIcon());

            // Has Children
            boolean hasChildren = false;
            Iterator customItemIterator = custom.iterator();
            while (customItemIterator.hasNext()) {
                final CustomStatusItem cItem = (CustomStatusItem)customItemIterator.next();
                String type = cItem.getType();
                if (type.equals(statusItem.getText())) {
                    hasChildren = true;
                }
            }

            if (!hasChildren) {
                // Add as Menu Item
                popup.add(statusAction);
            }
            else {

                final JMenu mainStatusItem = new JMenu(statusAction);


                popup.add(mainStatusItem);

                // Add Custom Messages
                customItemIterator = custom.iterator();
                while (customItemIterator.hasNext()) {
                    final CustomStatusItem customItem = (CustomStatusItem)customItemIterator.next();
                    String type = customItem.getType();
                    if (type.equals(statusItem.getText())) {
                        // Add Child Menu
                        Action action = new AbstractAction() {
                            public void actionPerformed(ActionEvent actionEvent) {
                                final String text = mainStatusItem.getText();
                                final StatusItem si = getStatusItem(text);
                                if (si == null) {
                                    // Custom status
                                    Log.error("Unable to find status item for status - " + text);
                                    return;
                                }

                                SwingWorker worker = new SwingWorker() {
                                    public Object construct() {
                                        Presence oldPresence = si.getPresence();
                                        Presence presence = copyPresence(oldPresence);
                                        presence.setStatus(customItem.getStatus());
                                        presence.setPriority(customItem.getPriority());
                                        SparkManager.getSessionManager().changePresence(presence);
                                        return "ok";
                                    }

                                    public void finished() {
                                        String status = customItem.getType() + " - " + customItem.getStatus();
                                        setStatus(status);
                                    }
                                };
                                worker.start();
                            }
                        };
                        action.putValue(Action.NAME, customItem.getStatus());
                        action.putValue(Action.SMALL_ICON, statusItem.getIcon());
                        mainStatusItem.add(action);
                    }
                }

                // If menu has children, allow it to still be clickable.
                mainStatusItem.addMouseListener(new MouseAdapter() {
                    public void mouseClicked(MouseEvent mouseEvent) {
                        statusAction.actionPerformed(null);
                        popup.setVisible(false);
                    }
                });
            }
        }

        // Add change message
        final JMenuItem changeStatusMenu = new JMenuItem(Res.getString("menuitem.set.status.message"), SparkRes.getImageIcon(SparkRes.BLANK_IMAGE));
        popup.addSeparator();


        popup.add(changeStatusMenu);
        changeStatusMenu.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                CustomMessages.addCustomMessage();
            }
        });


        Action editMessagesAction = new AbstractAction() {
            public void actionPerformed(ActionEvent actionEvent) {
                CustomMessages.editCustomMessages();
            }
        };

        editMessagesAction.putValue(Action.NAME, Res.getString("menuitem.edit.status.message"));
        popup.add(editMessagesAction);

        popup.show(statusPanel, 0, statusPanel.getHeight());
    }

    public void changeAvailability(final Presence presence) {
        if (!presence.isAvailable()) {
            return;
        }

        if ((presence.getMode() == currentPresence.getMode()) && (presence.getType() == currentPresence.getType()) && (presence.getStatus().equals(currentPresence.getStatus()))) {
            PacketExtension pe = presence.getExtension("x", "vcard-temp:x:update");
            if (pe != null) {
                // Update VCard
                loadVCard();
            }
            return;
        }

        final Runnable changePresenceRunnable = new Runnable() {
            public void run() {

                currentPresence = presence;

                setStatus(presence.getStatus());
                Icon icon = PresenceManager.getIconFromPresence(presence);
                if (icon != null) {
                    statusPanel.setIcon(icon);
                }
            }
        };

        SwingUtilities.invokeLater(changePresenceRunnable);
    }

    /**
     * Populates the current Dnd List.
     */
    private void buildStatusItemList() {
        for (Presence presence : PresenceManager.getPresences()) {
            Icon icon = PresenceManager.getIconFromPresence(presence);
            StatusItem item = new StatusItem(presence, icon);
            statusList.add(item);
        }

        final Icon availableIcon = PresenceManager.getIconFromPresence(new Presence(Presence.Type.available));

        // Set default presence icon (Avaialble)
        statusPanel.setIcon(availableIcon);
    }


    public Collection getStatusList() {
        return statusList;
    }

    public Presence getPresence() {
        return currentPresence;
    }

    public StatusItem getStatusItem(String label) {
        Iterator iter = statusList.iterator();
        while (iter.hasNext()) {
            StatusItem item = (StatusItem)iter.next();
            if (item.getText().equals(label)) {
                return item;
            }
        }
        return null;
    }

    public void paintComponent(Graphics g) {
        double scaleX = getWidth() / (double)backgroundImage.getWidth(null);
        double scaleY = getHeight() / (double)backgroundImage.getHeight(null);
        AffineTransform xform = AffineTransform.getScaleInstance(scaleX, scaleY);
        ((Graphics2D)g).drawImage(backgroundImage, xform, this);
    }


    public void loadVCard() {
        final SwingWorker worker = new SwingWorker() {
            public Object construct() {
                return SparkManager.getVCardManager().getVCard();
            }

            public void finished() {
                final VCard vCard = (VCard)get();
                populateWithVCardInfo(vCard);
            }
        };

        worker.start();
    }

    private void populateWithVCardInfo(VCard vCard) {
        if (vCard.getError() == null) {
            String firstName = vCard.getFirstName();
            String lastName = vCard.getLastName();
            String nickname = vCard.getNickName();
            if (ModelUtil.hasLength(firstName) && ModelUtil.hasLength(lastName)) {
                setNickname(firstName + " " + lastName);
            }
            else if (ModelUtil.hasLength(firstName)) {
                setNickname(firstName);
            }
            else if (ModelUtil.hasLength(nickname)) {
                setNickname(nickname);
            }
            else {
                nickname = SparkManager.getSessionManager().getUsername();
                setNickname(nickname);
            }
        }
        else {
            String nickname = SparkManager.getSessionManager().getUsername();
            setNickname(nickname);
            return;
        }


        byte[] avatarBytes = null;
        try {
            avatarBytes = vCard.getAvatar();
        }
        catch (Exception e) {
            Log.error("Cannot retrieve avatar bytes.", e);
        }


        if (avatarBytes != null) {
            try {
                ImageIcon avatarIcon = new ImageIcon(avatarBytes);
                avatarIcon = VCardManager.scale(avatarIcon);
                imageLabel.setIcon(avatarIcon);
                imageLabel.setBorder(BorderFactory.createBevelBorder(0, Color.white, Color.lightGray));
                imageLabel.invalidate();
                imageLabel.validate();
                imageLabel.repaint();
            }
            catch (Exception e) {
                // no issue
            }
        }
        else {
            imageLabel.setIcon(null);
            imageLabel.setBorder(null);
            imageLabel.invalidate();
            imageLabel.validate();
            imageLabel.repaint();
        }
    }

    public static Presence copyPresence(Presence presence) {
        return new Presence(presence.getType(), presence.getStatus(), presence.getPriority(), presence.getMode());
    }

    /**
     * Return the nickname Component used to display the users profile name.
     *
     * @return the label.
     */
    public JLabel getNicknameLabel() {
        return nicknameLabel;
    }

    private class StatusPanel extends JPanel {
        private JLabel iconLabel;
        private JLabel statusLabel;

        public StatusPanel() {
            super();

            setOpaque(false);

            iconLabel = new JLabel();
            statusLabel = new JLabel();

            setLayout(new GridBagLayout());

            // Remove padding from icon label
            iconLabel.setIconTextGap(0);

            add(iconLabel, new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0));
            add(statusLabel, new GridBagConstraints(1, 0, 1, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, 2, 0, 0), 0, 0));

            statusLabel.setFont(new Font("Dialog", Font.PLAIN, 11));
            statusLabel.setIcon(SparkRes.getImageIcon(SparkRes.DOWN_ARROW_IMAGE));
            statusLabel.setHorizontalTextPosition(JLabel.LEFT);

            setOpaque(false);

            final Border border = BorderFactory.createEmptyBorder(2, 2, 2, 2);
            setBorder(border);


            statusLabel.addMouseListener(new MouseAdapter() {
                public void mouseReleased(MouseEvent e) {
                    showPopup(e);
                }

                public void mouseEntered(MouseEvent e) {
                    setCursor(GraphicUtils.HAND_CURSOR);

                    setBorder(BorderFactory.createBevelBorder(0));
                }

                public void mouseExited(MouseEvent e) {
                    setCursor(GraphicUtils.DEFAULT_CURSOR);
                    setBorder(border);
                }

                public void mousePressed(MouseEvent e) {
                    setBorder(BorderFactory.createBevelBorder(1));
                }
            });

        }

        public void setStatus(String status) {
            int length = status.length();
            String visualStatus = status;
            if (length > 30) {
                visualStatus = status.substring(0, 27) + "...";
            }

            statusLabel.setText(visualStatus);
            statusLabel.setToolTipText(status);
        }

        public void setIcon(Icon icon) {
            iconLabel.setIcon(icon);
        }
    }

    public void setBackgroundImage(Image image) {
        this.backgroundImage = image;
    }

    public void setDescriptiveText(String text) {
        descriptiveLabel.setText(text);
    }

    public Dimension getPreferredSize() {
        Dimension dim = super.getPreferredSize();
        dim.width = 0;
        return dim;
    }
}
