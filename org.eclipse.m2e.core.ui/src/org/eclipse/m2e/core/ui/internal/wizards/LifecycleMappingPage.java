/*******************************************************************************
 * Copyright (c) 2011 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.m2e.core.ui.internal.wizards;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.viewers.CellEditor;
import org.eclipse.jface.viewers.ComboBoxCellEditor;
import org.eclipse.jface.viewers.EditingSupport;
import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.TreeViewerColumn;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.FontMetrics;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeColumn;
import org.eclipse.ui.PlatformUI;

import org.eclipse.m2e.core.internal.lifecyclemapping.LifecycleMappingFactory;
import org.eclipse.m2e.core.internal.lifecyclemapping.discovery.ILifecycleMappingRequirement;
import org.eclipse.m2e.core.internal.lifecyclemapping.discovery.IMavenDiscoveryProposal;
import org.eclipse.m2e.core.internal.lifecyclemapping.discovery.LifecycleMappingConfiguration;
import org.eclipse.m2e.core.internal.lifecyclemapping.discovery.MojoExecutionMappingConfiguration;
import org.eclipse.m2e.core.internal.lifecyclemapping.discovery.PackagingTypeMappingConfiguration;
import org.eclipse.m2e.core.internal.lifecyclemapping.discovery.ProjectLifecycleMappingConfiguration;
import org.eclipse.m2e.core.project.MavenProjectInfo;
import org.eclipse.m2e.core.project.ProjectImportConfiguration;
import org.eclipse.m2e.core.ui.internal.M2EUIPluginActivator;
import org.eclipse.m2e.core.ui.internal.MavenImages;
import org.eclipse.m2e.core.ui.internal.Messages;
import org.eclipse.m2e.core.ui.internal.lifecyclemapping.AggregateMappingLabelProvider;
import org.eclipse.m2e.core.ui.internal.lifecyclemapping.ILifecycleMappingLabelProvider;
import org.eclipse.m2e.core.ui.internal.lifecyclemapping.MojoExecutionMappingLabelProvider;
import org.eclipse.m2e.core.ui.internal.lifecyclemapping.PackagingTypeMappingLabelProvider;


/**
 * LifecycleMappingPage
 * 
 * @author igor
 */
@SuppressWarnings("restriction")
public class LifecycleMappingPage extends WizardPage {

  private static final String EMPTY_STRING = ""; //$NON-NLS-1$

  private static final int MAVEN_INFO_IDX = 0;

  private static final int ACTION_INFO_IDX = 1;

  private LifecycleMappingConfiguration mappingConfiguration;

  private TreeViewer treeViewer;

  private Button autoSelectButton;

  private boolean loading;

  private Text details;

  private Text license;

  /**
   * Create the wizard.
   */
  public LifecycleMappingPage() {
    super("LifecycleMappingPage"); //$NON-NLS-1$
    setTitle(Messages.LifecycleMappingPage_title);
    setDescription(Messages.LifecycleMappingPage_description);
    setPageComplete(true); // always allow to leave mapping page, even when there are mapping problems
  }

  /**
   * Create contents of the wizard.
   * 
   * @param parent
   */
  public void createControl(Composite parent) {
    Composite container = new Composite(parent, SWT.NULL);

    setControl(container);
    container.setLayout(new GridLayout(1, false));

    treeViewer = new TreeViewer(container, SWT.BORDER | SWT.FULL_SELECTION);

    Tree tree = treeViewer.getTree();
    tree.setLinesVisible(true);
    tree.setHeaderVisible(true);
    tree.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));

    TreeViewerColumn treeViewerColumn = new TreeViewerColumn(treeViewer, SWT.NONE);
    TreeColumn trclmnNewColumn = treeViewerColumn.getColumn();
    trclmnNewColumn.setText(Messages.LifecycleMappingPage_mavenBuildColumnTitle);

    TreeViewerColumn columnViewerAction = new TreeViewerColumn(treeViewer, SWT.NONE);
    TreeColumn columnAction = columnViewerAction.getColumn();
    columnAction.setText(Messages.LifecycleMappingPage_actionColumnTitle);
    columnViewerAction.setEditingSupport(new EditingSupport(treeViewer) {

      @Override
      protected void setValue(Object element, Object value) {
        if(element instanceof ILifecycleMappingLabelProvider) {
          ILifecycleMappingLabelProvider prov = (ILifecycleMappingLabelProvider) element;
          Integer val = (Integer) value;
          List<IMavenDiscoveryProposal> all = mappingConfiguration.getProposals(prov.getKey());
          IMavenDiscoveryProposal prop = mappingConfiguration.getSelectedProposal(prov.getKey());
          if(prop != null) {
            mappingConfiguration.removeSelectedProposal(prop);
          }
          if(val.intValue() < all.size()) {
            IMavenDiscoveryProposal sel = all.get(val.intValue());
            if(sel != null) {
              mappingConfiguration.addSelectedProposal(sel);
            }
          }
          getViewer().refresh(true);
        }
      }

      @Override
      protected Object getValue(Object element) {
        if(element instanceof ILifecycleMappingLabelProvider) {
          ILifecycleMappingLabelProvider prov = (ILifecycleMappingLabelProvider) element;
          IMavenDiscoveryProposal prop = mappingConfiguration.getSelectedProposal(prov.getKey());
          List<IMavenDiscoveryProposal> all = mappingConfiguration.getProposals(prov.getKey());
          int index = all.indexOf(prop);
          return index >= 0 ? Integer.valueOf(index) : Integer.valueOf(all.size());
        }
        return Integer.valueOf(0);
      }

      @Override
      protected CellEditor getCellEditor(Object element) {
        if(element instanceof ILifecycleMappingLabelProvider) {
          ILifecycleMappingLabelProvider prov = (ILifecycleMappingLabelProvider) element;
          List<IMavenDiscoveryProposal> all = mappingConfiguration.getProposals(prov.getKey());
          List<String> values = new ArrayList<String>();
          for(IMavenDiscoveryProposal prop : all) {
            values.add(NLS.bind(Messages.LifecycleMappingPage_installDescription, prop.toString()));
          }
          values.add(Messages.LifecycleMappingPage_takeNoActionDescription);
          ComboBoxCellEditor edit = new ComboBoxCellEditor(treeViewer.getTree(), values.toArray(new String[0]));
          return edit;
        }
        throw new IllegalStateException();
      }

      @Override
      protected boolean canEdit(Object element) {
        if(element instanceof ILifecycleMappingLabelProvider) {
          ILifecycleMappingLabelProvider prov = (ILifecycleMappingLabelProvider) element;
          List<IMavenDiscoveryProposal> all = mappingConfiguration.getProposals(prov.getKey());
          return all != null && !all.isEmpty();
        }
        return false;
      }
    });

    treeViewer.setContentProvider(new ITreeContentProvider() {

      public void dispose() {
      }

      public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
      }

      public Object[] getElements(Object inputElement) {
        if(inputElement instanceof LifecycleMappingConfiguration) {
          Map<ILifecycleMappingRequirement, List<ILifecycleMappingLabelProvider>> packagings = new HashMap<ILifecycleMappingRequirement, List<ILifecycleMappingLabelProvider>>();
          Map<ILifecycleMappingRequirement, List<ILifecycleMappingLabelProvider>> mojos = new HashMap<ILifecycleMappingRequirement, List<ILifecycleMappingLabelProvider>>();
          Collection<ProjectLifecycleMappingConfiguration> projects = ((LifecycleMappingConfiguration) inputElement)
              .getProjects();
          for(ProjectLifecycleMappingConfiguration prjconf : projects) {
            PackagingTypeMappingConfiguration pack = prjconf.getPackagingTypeMappingConfiguration();
            if(pack != null) {
              ILifecycleMappingRequirement packReq = pack.getLifecycleMappingRequirement();
              if(packReq != null && !mappingConfiguration.getProposals(packReq).isEmpty()) {
                List<ILifecycleMappingLabelProvider> val = packagings.get(pack);
                if(val == null) {
                  val = new ArrayList<ILifecycleMappingLabelProvider>();
                  packagings.put(packReq, val);
                }
                val.add(new PackagingTypeMappingLabelProvider(prjconf, pack));
              }
            }
            List<MojoExecutionMappingConfiguration> mojoExecs = prjconf.getMojoExecutionConfigurations();
            if(mojoExecs != null) {
              for(MojoExecutionMappingConfiguration mojoMap : mojoExecs) {
                ILifecycleMappingRequirement mojoReq = mojoMap.getLifecycleMappingRequirement();
                // include mojo execution if it has available proposals or interesting phase not mapped locally
                if(mojoReq != null
                    && !mappingConfiguration.getProposals(mojoReq).isEmpty()
                    || (LifecycleMappingFactory.isInterestingPhase(mojoMap.getExecution().getLifecyclePhase()) && !mappingConfiguration
                        .isRequirementSatisfied(mojoReq, true))) {
                  List<ILifecycleMappingLabelProvider> val = mojos.get(mojoMap);
                  if(val == null) {
                    val = new ArrayList<ILifecycleMappingLabelProvider>();
                    mojos.put(mojoReq, val);
                  }
                  val.add(new MojoExecutionMappingLabelProvider(prjconf, mojoMap));
                }
              }
            }
          }
          List<ILifecycleMappingLabelProvider> toRet = new ArrayList<ILifecycleMappingLabelProvider>();
          for(Map.Entry<ILifecycleMappingRequirement, List<ILifecycleMappingLabelProvider>> ent : packagings.entrySet()) {
            toRet.add(new AggregateMappingLabelProvider(ent.getKey(), ent.getValue()));
          }
          for(Map.Entry<ILifecycleMappingRequirement, List<ILifecycleMappingLabelProvider>> ent : mojos.entrySet()) {
            toRet.add(new AggregateMappingLabelProvider(ent.getKey(), ent.getValue()));
          }
          return toRet.toArray();
        }
        return null;
      }

      public Object[] getChildren(Object parentElement) {
        if(parentElement instanceof AggregateMappingLabelProvider) {
          return ((AggregateMappingLabelProvider) parentElement).getChildren();
        }
        return new Object[0];
      }

      public Object getParent(Object element) {
        return null;
      }

      public boolean hasChildren(Object element) {
        Object[] children = getChildren(element);
        return children != null && children.length > 0;
      }

    });
    treeViewer.setLabelProvider(new ITableLabelProvider() {

      public void removeListener(ILabelProviderListener listener) {
      }

      public boolean isLabelProperty(Object element, String property) {
        return false;
      }

      public void dispose() {
      }

      public void addListener(ILabelProviderListener listener) {
      }

      public String getColumnText(Object element, int columnIndex) {
        if(element instanceof ILifecycleMappingLabelProvider) {
          ILifecycleMappingLabelProvider prov = (ILifecycleMappingLabelProvider) element;
          if(columnIndex == MAVEN_INFO_IDX) {
            return prov.getMavenText();
          }
          if(columnIndex == ACTION_INFO_IDX) {
            IMavenDiscoveryProposal proposal = mappingConfiguration.getSelectedProposal(prov.getKey());
            if(proposal != null) {
              return NLS.bind(Messages.LifecycleMappingPage_installDescription, proposal.toString()); //not really feeling well here. 
            }
            if(loading) {
              return EMPTY_STRING;
            } else {
              return mappingConfiguration.getProposals(prov.getKey()).isEmpty() ? EMPTY_STRING
                  : Messages.LifecycleMappingPage_takeNoActionDescription;//"Nothing discovered"; 
            }
          }
        }
        return null;
      }

      public Image getColumnImage(Object element, int columnIndex) {
        if(element instanceof ILifecycleMappingLabelProvider) {
          ILifecycleMappingLabelProvider prov = (ILifecycleMappingLabelProvider) element;
          if(columnIndex == 0 && prov.isError(mappingConfiguration)) {
            if(mappingConfiguration.getSelectedProposal(prov.getKey()) == null) {
              return MavenImages.IMG_ERROR;
            } else {
              return MavenImages.IMG_MSG_INFO;
            }
          }
        }
        return null;
      }
    });

    treeViewer.addSelectionChangedListener(new ISelectionChangedListener() {

      public void selectionChanged(SelectionChangedEvent event) {
        if(event.getSelection() instanceof IStructuredSelection
            && ((IStructuredSelection) event.getSelection()).size() == 1) {
          ILifecycleMappingLabelProvider prov = (ILifecycleMappingLabelProvider) ((IStructuredSelection) event
              .getSelection()).getFirstElement();
          IMavenDiscoveryProposal proposal = mappingConfiguration.getSelectedProposal(prov.getKey());
          details.setText(proposal != null ? proposal.getDescription() : mappingConfiguration.getProposals(
              prov.getKey()).isEmpty() ? NLS.bind(
              Messages.LifecycleMappingPage_noMarketplaceEntryDescription,
              prov.getMavenText()) : EMPTY_STRING);
          license.setText(proposal == null ? EMPTY_STRING : proposal.getLicense());
        } else {
          details.setText(EMPTY_STRING);
          license.setText(EMPTY_STRING);
        }
      }
    });

    Composite composite = new Composite(container, SWT.NONE);
    composite.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
    composite.setLayout(new GridLayout(2, false));

    Button btnNewButton_1 = new Button(composite, SWT.NONE);
    btnNewButton_1.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        mappingConfiguration.clearSelectedProposals();
        treeViewer.refresh();
        getWizard().getContainer().updateButtons(); // needed to enable/disable Finish button
      }
    });
    btnNewButton_1.setText(Messages.LifecycleMappingPage_deselectAllButton);

    autoSelectButton = new Button(composite, SWT.NONE);
    autoSelectButton.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        discoverProposals();
      }
    });
    autoSelectButton.setText(Messages.LifecycleMappingPage_autoSelectButton);

    // Provide a reasonable height for the details box 
    GC gc = new GC(container);
    gc.setFont(JFaceResources.getDialogFont());
    FontMetrics fontMetrics = gc.getFontMetrics();
    gc.dispose();

    Group grpLicense = new Group(container, SWT.NONE);
    grpLicense.setLayout(new GridLayout(1, false));
    grpLicense.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false, 1, 1));
    grpLicense.setText(Messages.LifecycleMappingPage_licenseLabel);

    license = new Text(grpLicense, SWT.READ_ONLY);
    GridData gd = new GridData(SWT.FILL, SWT.FILL, true, true);
    gd.heightHint = Dialog.convertHeightInCharsToPixels(fontMetrics, 1);
    gd.minimumHeight = Dialog.convertHeightInCharsToPixels(fontMetrics, 1);
    license.setLayoutData(gd);

    Group grpDetails = new Group(container, SWT.NONE);
    grpDetails.setLayout(new GridLayout(1, false));
    grpDetails.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false, 1, 1));
    grpDetails.setText(Messages.LifecycleMappingPage_detailsLabel);

    details = new Text(grpDetails, SWT.WRAP | SWT.READ_ONLY | SWT.V_SCROLL);
    gd = new GridData(SWT.FILL, SWT.FILL, true, true);
    gd.heightHint = Dialog.convertHeightInCharsToPixels(fontMetrics, 3);
    gd.minimumHeight = Dialog.convertHeightInCharsToPixels(fontMetrics, 1);
    details.setLayoutData(gd);
  }

  protected void discoverProposals() {
    loading = true;
    treeViewer.refresh();
    try {
      getContainer().run(true, true, new IRunnableWithProgress() {
        public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
          mappingConfiguration.clearSelectedProposals();
          ((MavenImportWizard) getWizard()).discoverProposals(mappingConfiguration, monitor);
          mappingConfiguration.autoCompleteMapping();
        }
      });
    } catch(InvocationTargetException e) {
      setErrorMessage(e.getMessage());
    } catch(InterruptedException e) {
      setErrorMessage(e.getMessage());
    }
    loading = false;
    treeViewer.refresh();
    getWizard().getContainer().updateButtons(); // needed to enable/disable Finish button
  }

  @Override
  public void setVisible(boolean visible) {
    super.setVisible(visible);
    if(visible) {
      PlatformUI.getWorkbench().getHelpSystem()
          .setHelp(getWizard().getContainer().getShell(), M2EUIPluginActivator.PLUGIN_ID + ".LifecycleMappingPage"); //$NON-NLS-1$
      mappingConfiguration = ((MavenImportWizard) getWizard()).getMappingConfiguration();
      if(!mappingConfiguration.isMappingComplete()) {
        // try to solve problems only if there are any
        mappingConfiguration.autoCompleteMapping();
      }
      treeViewer.setInput(mappingConfiguration);

      //set initial column sizes
      TreeColumn[] columns = treeViewer.getTree().getColumns();
      for(int i = 0; i < columns.length; i++ ) {
        int ratio = i == 0 ? 6 : 4;
        columns[i].setWidth(treeViewer.getTree().getClientArea().width / 10 * ratio);
      }
    }
  }

  public boolean canFlipToNextPage() {
    return getNextPage() != null;
  }

  protected Collection<MavenProjectInfo> getProjects() {
    return ((MavenImportWizard) getWizard()).getProjects();
  }

  protected ProjectImportConfiguration getProjectImportConfiguration() {
    return ((MavenImportWizard) getWizard()).getProjectImportConfiguration();
  }

  public List<IMavenDiscoveryProposal> getSelectedDiscoveryProposals() {
    if(mappingConfiguration == null) {
      return Collections.emptyList();
    }
    return mappingConfiguration.getSelectedProposals();
  }

  public boolean isMappingComplete() {
    return mappingConfiguration == null || mappingConfiguration.isMappingComplete();
  }
}
