package shortestpath;

import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.DragAndDropReorderPane;
import net.runelite.client.ui.components.MouseDragEventForwarder;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

public class ShortestPathPluginPanel extends PluginPanel {
    private ShortestPathPlugin plugin;

    private final DragAndDropReorderPane pluginListPanel;

    public ShortestPathPluginPanel(ShortestPathPlugin plugin) {
        this.plugin = plugin;

        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(10, 10, 10, 10));

        JPanel northPanel = new JPanel(new BorderLayout());
        northPanel.setBorder(new EmptyBorder(1,1,10,0));

        JPanel titlePanel = new JPanel(new BorderLayout());
        titlePanel.setBorder(new EmptyBorder(1, 3, 10, 7));

        JLabel title = new JLabel();
        title.setText("Shortest Path");
        title.setForeground(Color.WHITE);
        titlePanel.add(title, BorderLayout.WEST);

        pluginListPanel = new DragAndDropReorderPane();
        pluginListPanel.addDragListener(component -> {
            Component[] components = component.getParent().getComponents();
            List<PluginIdentifier> plugins = new ArrayList<>(plugin.getPathManager().getPluginPriorities().size());
            for (Component c : components) {
                if (c instanceof PluginPathPanel) {
                    PluginPathPanel pathPanel = (PluginPathPanel)c;
                    plugins.add(pathPanel.ownerPlugin);
                }
            }
            plugin.setNewPriorities(plugins);
        });

        pluginListPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);

        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        centerPanel.add(pluginListPanel, BorderLayout.NORTH);

        add(titlePanel, BorderLayout.NORTH);
        add(centerPanel, BorderLayout.CENTER);

        repaintAsync();
    }

    void repaintAsync() {
        SwingUtilities.invokeLater(() -> {
            pluginListPanel.removeAll();
            PluginPathManager pm = plugin.getPathManager();
            List<PluginIdentifier> pluginIdentifiers = pm.getPluginPriorities();

            for (PluginIdentifier pi : pluginIdentifiers) {
                pluginListPanel.add(new PluginPathPanel(pi, pm.getParameters(pi), pluginListPanel));
            }

            pluginListPanel.revalidate();
            revalidate();
            repaint();
        });
    }

    private class PluginPathPanel extends JPanel {
        private final String VISIBLE_ICON = "\uD83D\uDC41";
        private final String HIDDEN_ICON = "⬭";
        private final JLabel pluginName;

        private final PluginIdentifier ownerPlugin;

        PluginPathPanel(PluginIdentifier ownerPlugin, PathParameters parameters, JComponent panel) {
            this.ownerPlugin = ownerPlugin;

            setLayout(new BorderLayout());
            setBorder(new EmptyBorder(5, 0, 0, 0));

            JPanel mainLayout = new JPanel();
            mainLayout.setLayout(new BorderLayout());
            mainLayout.setBackground(ColorScheme.DARKER_GRAY_COLOR);
            mainLayout.setBorder(new EmptyBorder(4, 4, 4, 4));

            pluginName = new JLabel();
            pluginName.setText(ownerPlugin.getName());

            JLabel visiblityPath = new JLabel();
            if (parameters != null) {
                visiblityPath.setText(parameters.isVisible() ? VISIBLE_ICON : HIDDEN_ICON);
                visiblityPath.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        plugin.requestPath(parameters.toggleVisibility());
                    }
                });
            }

            MouseDragEventForwarder mouseDragEventForwarder = new MouseDragEventForwarder(panel);
            addMouseMotionListener(mouseDragEventForwarder);
            addMouseListener(mouseDragEventForwarder);
            pluginName.addMouseMotionListener(mouseDragEventForwarder);
            pluginName.addMouseListener(mouseDragEventForwarder);

            mainLayout.add(pluginName, BorderLayout.CENTER);

            if (parameters != null) {
                mainLayout.add(visiblityPath, BorderLayout.LINE_END);
            }

            add(mainLayout, BorderLayout.CENTER);
        }
    }
}
