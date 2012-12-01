package org.jetbrains.kannotator.plugin.actions.dialog;

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.components.JBScrollPane;
import jet.runtime.typeinfo.KotlinSignature;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.io.File;
import java.util.Collection;

public class InferAnnotationDialog extends DialogWrapper {
    JPanel contentPanel;
    JCheckBox nullabilityCheckBox;
    JCheckBox kotlinSignaturesCheckBox;
    TextFieldWithBrowseButton outputDirectory;
    JBScrollPane jarsTreeScrollPane;
    JLabel outputDirectoryLabel;
    JLabel jarsTreeLabel;

    // Not from gui designer
    LibraryCheckboxTree libraryTree;

    @NotNull
    private Project project;

    public InferAnnotationDialog(@NotNull Project project) {
        super(project);

        this.project = project;
        setDefaultValues();

        init();

        updateControls();
    }

    @Override
    protected void init() {
        setTitle("Annotate Jar Files");

        contentPanel.setPreferredSize(new Dimension(440, 500));

        outputDirectory.addBrowseFolderListener(
                RefactoringBundle.message("select.target.directory"),
                "Inferred annotation will be written to this folder",
                null, FileChooserDescriptorFactory.createSingleFolderDescriptor());

        outputDirectory.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
            protected void textChanged(final DocumentEvent e) {
                updateControls();
            }
        });

        outputDirectoryLabel.setLabelFor(outputDirectory.getTextField());

        nullabilityCheckBox.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                updateControls();
            }
        });

        kotlinSignaturesCheckBox.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                updateControls();
            }
        });

        LibraryItemsTreeController libraryItemsTreeController = new LibraryItemsTreeController();
        libraryTree = new LibraryCheckboxTree(libraryItemsTreeController);
        libraryItemsTreeController.buildTree(libraryTree, project);

        jarsTreeScrollPane.setViewportView(libraryTree);

        jarsTreeLabel.setLabelFor(libraryTree);

        super.init();
    }

    protected void setDefaultValues() {
        nullabilityCheckBox.setSelected(true);
        kotlinSignaturesCheckBox.setSelected(true);
        outputDirectory.getTextField().setText(new File(FileUtil.toSystemDependentName(project.getBaseDir().getPath()), "annotations").getAbsolutePath());
    }

    @Nullable
    private String getConfiguringOutputPath() {
        String outputPath = FileUtil.toSystemIndependentName(outputDirectory.getText().trim());
        if (outputPath.length() == 0) {
            outputPath = null;
        }
        return outputPath;
    }

    @NotNull
    public String getConfiguredOutputPath() {
        String configuredOutputPath = getConfiguringOutputPath();
        if (configuredOutputPath == null) {
            throw new IllegalStateException("Output path wasn't properly configured");
        }

        return configuredOutputPath;
    }

    @KotlinSignature("fun getCheckedJarFiles() : Collection<VirtualFile>")
    public Collection<VirtualFile> getCheckedJarFiles() {
        return libraryTree.getController().getCheckedJarFiles();
    }

    public boolean shouldInferNullabilityAnnotations() {
        return nullabilityCheckBox.isSelected();
    }

    public boolean shouldInferKotlinAnnotations() {
        return kotlinSignaturesCheckBox.isSelected();
    }

    protected void updateControls() {
        boolean someAnnotationTypeSelected = shouldInferNullabilityAnnotations() || shouldInferKotlinAnnotations();
        setOKActionEnabled(getConfiguringOutputPath() != null && someAnnotationTypeSelected);
    }

    @Nullable
    @Override
    public JComponent getPreferredFocusedComponent() {
        return libraryTree;
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
        return contentPanel;
    }
}
