/*
 * ------------------------------------------------------------------------
 *
 *  Copyright (C) 2003 - 2011
 *  University of Konstanz, Germany and
 *  KNIME GmbH, Konstanz, Germany
 *  Website: http://www.knime.org; Email: contact@knime.org
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License, Version 3, as
 *  published by the Free Software Foundation.
 *
 *  This program is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, see <http://www.gnu.org/licenses>.
 *
 *  Additional permission under GNU GPL version 3 section 7:
 *
 *  KNIME interoperates with ECLIPSE solely via ECLIPSE's plug-in APIs.
 *  Hence, KNIME and ECLIPSE are both independent programs and are not
 *  derived from each other. Should, however, the interpretation of the
 *  GNU GPL Version 3 ("License") under any applicable laws result in
 *  KNIME and ECLIPSE being a combined program, KNIME GMBH herewith grants
 *  you the additional permission to use and propagate KNIME together with
 *  ECLIPSE with only the license terms in place for ECLIPSE applying to
 *  ECLIPSE and the GNU GPL Version 3 applying for KNIME, provided the
 *  license terms of ECLIPSE themselves allow for the respective use and
 *  propagation of ECLIPSE together with KNIME.
 *
 *  Additional permission relating to nodes for KNIME that extend the Node
 *  Extension (and in particular that are based on subclasses of NodeModel,
 *  NodeDialog, and NodeView) and that only interoperate with KNIME through
 *  standard APIs ("Nodes"):
 *  Nodes are deemed to be separate and independent programs and to not be
 *  covered works.  Notwithstanding anything to the contrary in the
 *  License, the License does not apply to Nodes, you are not required to
 *  license Nodes under the License, and you are granted a license to
 *  prepare and propagate Nodes, in each case even if such Nodes are
 *  propagated with or for interoperation with KNIME. The owner of a Node
 *  may freely choose the license terms applicable to such Node, including
 *  when such Node is propagated with or for interoperation with KNIME.
 * ------------------------------------------------------------------------
 *
 * History
 *   Sep 5, 2012 (Patrick Winter): created
 */
package org.openml.knime.taskloader;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.ExecutionMonitor;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeModel;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.port.PortObject;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.node.port.PortType;
import org.knime.core.node.port.flowvariable.FlowVariablePortObject;
import org.knime.core.node.port.flowvariable.FlowVariablePortObjectSpec;
import org.openMl.openml.TaskDocument;
import org.openMl.openml.TaskInput;
import org.openMl.openml.TaskParameter;
import org.openml.dataSetDescription.DataSetDescriptionDocument;
import org.openml.knime.OpenMLVariables;
import org.openml.knime.OpenMLWebservice;

/**
 * This is the model implementation.
 * 
 * 
 * @author Patrick Winter, KNIME.com, Zurich, Switzerland
 */
public class TaskLoaderNodeModel extends NodeModel {

    private TaskLoaderConfiguration m_configuration;

    /**
     * Constructor for the node model.
     */
    protected TaskLoaderNodeModel() {
        super(new PortType[]{}, new PortType[]{FlowVariablePortObject.TYPE});
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected PortObject[] execute(final PortObject[] inData,
            final ExecutionContext exec) throws Exception {
        try {
            TaskDocument taskDoc =
                    TaskDocument.Factory.parse(OpenMLWebservice
                            .getTaskURL(m_configuration.getTaskid()));
            int taskId = taskDoc.getTask().getTaskId().intValue();
            pushFlowVariableInt(OpenMLVariables.TASKID, taskId);
            Integer numRepeats = null;
            Integer numFolds = null;
            Integer datasetID = null;
            String splitsID = null;
            TaskInput[] taskInputs = taskDoc.getTask().getInputArray();
            for (int i = 0; i < taskInputs.length; i++) {
                TaskParameter[] parameters = null;
                if (taskInputs[i].isSetDataSet()) {
                    datasetID =
                            taskInputs[i].getDataSet().getDataSetId()
                                    .intValue();
                } else if (taskInputs[i].isSetEstimationProcedure()) {
                    splitsID =
                            taskInputs[i].getEstimationProcedure()
                                    .getDataSplitsId();
                    parameters =
                            taskInputs[i].getEstimationProcedure()
                                    .getParameterArray();
                    for (int j = 0; j < parameters.length; j++) {
                        TaskParameter param = parameters[j];
                        if (param.getName().equals("number_folds")) {
                            numFolds = Integer.parseInt(param.getStringValue());
                        } else if (param.getName().equals("number_repeats")) {
                            numRepeats =
                                    Integer.parseInt(param.getStringValue());
                        }
                    }
                }
            }
            if (numRepeats == null || numFolds == null || datasetID == null
                    || splitsID == null) {
                throw new Exception("Invalid response from server");
            }

            // TODO remove this when fixed on the server
            numRepeats = 2;

            pushFlowVariableInt(OpenMLVariables.REPEATS, numRepeats);
            pushFlowVariableInt(OpenMLVariables.FOLDS, numFolds);
            InputStream datasetIn =
                    OpenMLWebservice.getDatasetDescURL(+datasetID).openStream();
            DataSetDescriptionDocument datasetDoc =
                    DataSetDescriptionDocument.Factory.parse(datasetIn);
            String datasetURL = datasetDoc.getDataSetDescription().getUrl();

            // TODO remove this when fixed on the server
            datasetURL =
                    "https://github.com/joaquinvanschoren/OpenML/raw/master/ARFF/iris.arff";

            pushFlowVariableString(OpenMLVariables.DATASETURL, datasetURL);
            String splitsURL =
                    OpenMLWebservice.getSplitsURL(splitsID).toString();

            // TODO remove this when fixed on the server
            splitsURL =
                    "https://github.com/joaquinvanschoren/OpenML/raw/master/ARFF/folds_task_1.arff";

            pushFlowVariableString(OpenMLVariables.SPLITSURL, splitsURL);
            String idRow =
                    datasetDoc.getDataSetDescription().getRowIdAttribute();
            if (idRow != null) {
                pushFlowVariableString(OpenMLVariables.IDROW, idRow);
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
        return new PortObject[]{FlowVariablePortObject.INSTANCE};
    }

    /**
     * Returns the configured ID of this task.
     * 
     * 
     * @return ID of the task
     */
    public String getTaskid() {
        return m_configuration.getTaskid();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void reset() {
        // Not used
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected PortObjectSpec[] configure(final PortObjectSpec[] inSpecs)
            throws InvalidSettingsException {
        return new PortObjectSpec[]{FlowVariablePortObjectSpec.INSTANCE};
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void saveSettingsTo(final NodeSettingsWO settings) {
        if (m_configuration != null) {
            m_configuration.save(settings);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void loadValidatedSettingsFrom(final NodeSettingsRO settings)
            throws InvalidSettingsException {
        TaskLoaderConfiguration config = new TaskLoaderConfiguration();
        config.loadAndValidate(settings);
        m_configuration = config;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void validateSettings(final NodeSettingsRO settings)
            throws InvalidSettingsException {
        new TaskLoaderConfiguration().loadAndValidate(settings);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void loadInternals(final File internDir,
            final ExecutionMonitor exec) throws IOException,
            CanceledExecutionException {
        // Not used
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void saveInternals(final File internDir,
            final ExecutionMonitor exec) throws IOException,
            CanceledExecutionException {
        // Not used
    }

}
