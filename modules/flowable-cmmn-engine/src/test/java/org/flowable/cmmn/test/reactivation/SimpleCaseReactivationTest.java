/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.flowable.cmmn.test.reactivation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.flowable.cmmn.api.runtime.PlanItemInstanceState.ACTIVE;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.flowable.cmmn.api.history.HistoricCaseInstance;
import org.flowable.cmmn.api.history.HistoricPlanItemInstance;
import org.flowable.cmmn.api.runtime.CaseInstance;
import org.flowable.cmmn.api.runtime.PlanItemInstance;
import org.flowable.cmmn.api.runtime.PlanItemInstanceState;
import org.flowable.cmmn.converter.CmmnXMLException;
import org.flowable.cmmn.engine.test.CmmnDeployment;
import org.flowable.cmmn.engine.test.FlowableCmmnTestCase;
import org.flowable.common.engine.api.FlowableIllegalArgumentException;
import org.flowable.common.engine.api.FlowableIllegalStateException;
import org.flowable.common.engine.api.FlowableObjectNotFoundException;
import org.flowable.common.engine.impl.identity.Authentication;
import org.flowable.variable.api.history.HistoricVariableInstance;
import org.flowable.variable.api.persistence.entity.VariableInstance;
import org.junit.Test;

public class SimpleCaseReactivationTest extends FlowableCmmnTestCase {
    @Test
    @CmmnDeployment(resources = "org/flowable/cmmn/test/reactivation/Simple_Reactivation_Test_Case_No_Event.cmmn.xml")
    public void simpleCaseReactivationMissingCaseFailureTest() {
        CaseInstance caze = cmmnRuntimeService.createCaseInstanceBuilder()
            .caseDefinitionKey("simpleReactivationTestCaseNoEvent")
            .start();

        assertThatThrownBy(() -> cmmnHistoryService.reactivateHistoricCaseInstance("nonexistentCaseId", null))
                .isExactlyInstanceOf(FlowableObjectNotFoundException.class)
                .hasMessageContaining("No historic case instance to be reactivated found with id: nonexistentCaseId");
    }

    @Test
    @CmmnDeployment(resources = "org/flowable/cmmn/test/reactivation/Simple_Reactivation_Test_Case_No_Event.cmmn.xml")
    public void simpleCaseReactivationActiveCaseFailureTest() {
        CaseInstance caze = cmmnRuntimeService.createCaseInstanceBuilder()
            .caseDefinitionKey("simpleReactivationTestCaseNoEvent")
            .start();

        assertThatThrownBy(() -> cmmnHistoryService.reactivateHistoricCaseInstance(caze.getId(), null))
                .isExactlyInstanceOf(FlowableIllegalStateException.class)
                .hasMessageContaining("Case instance is still running, cannot reactivate historic case instance: " + caze.getId());
    }

    @Test
    @CmmnDeployment(resources = "org/flowable/cmmn/test/reactivation/Simple_Reactivation_Test_Case_No_Event.cmmn.xml")
    public void simpleCaseReactivationNoReactivationEventFailureTest() {
        final HistoricCaseInstance caze = createAndFinishSimpleCase("simpleReactivationTestCaseNoEvent");

        assertThatThrownBy(() -> cmmnHistoryService.reactivateHistoricCaseInstance(caze.getId(), null))
                .isExactlyInstanceOf(FlowableIllegalStateException.class)
                .hasMessageContaining("The historic case instance " + caze.getId() +
                    " cannot be reactivated as there is no reactivation event in its CMMN model. You need to explicitly model the reactivation event in order to support case reactivation.");
    }

    @Test
    public void simpleCaseReactivationMultiReactivationEventFailureTest() {
        assertThatThrownBy(() -> addDeploymentForAutoCleanup(cmmnRepositoryService.createDeployment()
                .addClasspathResource("org/flowable/cmmn/test/reactivation/Simple_Reactivation_Test_Case_Multi_Reactivation_Elements.cmmn.xml")
                .deploy()
            ))
                .isExactlyInstanceOf(CmmnXMLException.class)
                .hasRootCauseInstanceOf(FlowableIllegalArgumentException.class)
                .getRootCause()
                .hasMessageContaining("There can only be one reactivation listener on a case model, not multiple ones. Use a start form on the listener, "
                    + "if there are several options on how to reactivate a case and use conditions to handle the different options on reactivation.");
    }

    @Test
    @CmmnDeployment(resources = "org/flowable/cmmn/test/reactivation/Simple_Reactivation_Test_Case.cmmn.xml")
    public void reactivationListenerNotAvailableAtCaseRuntime() {
        String previousUserId = Authentication.getAuthenticatedUserId();
        try {
            Authentication.setAuthenticatedUserId("JohnDoe");
            CaseInstance caseInstance = cmmnRuntimeService.createCaseInstanceBuilder()
                .caseDefinitionKey("simpleReactivationTestCase")
                .start();

            assertThat(caseInstance).isNotNull();
            List<PlanItemInstance> planItemInstances = getAllPlanItemInstances(caseInstance.getId());
            assertThat(planItemInstances).isNotNull().hasSize(5);
            assertPlanItemInstanceState(caseInstance, "Reactivate case", PlanItemInstanceState.UNAVAILABLE);
        } finally {
            Authentication.setAuthenticatedUserId(previousUserId);
        }
    }

    @Test
    @CmmnDeployment(resources = "org/flowable/cmmn/test/reactivation/Simple_Reactivation_Test_Case.cmmn.xml")
    public void reactivationListenerHavingNoImpactAtCaseCompletion() {
        String previousUserId = Authentication.getAuthenticatedUserId();
        try {
            Authentication.setAuthenticatedUserId("JohnDoe");
            CaseInstance caseInstance = cmmnRuntimeService.createCaseInstanceBuilder()
                .caseDefinitionKey("simpleReactivationTestCase")
                .start();
            List<PlanItemInstance> planItemInstances = getPlanItemInstances(caseInstance.getId());
            cmmnRuntimeService.triggerPlanItemInstance(getPlanItemInstanceIdByName(planItemInstances, "Task A"));
            planItemInstances = getPlanItemInstances(caseInstance.getId());
            cmmnRuntimeService.triggerPlanItemInstance(getPlanItemInstanceIdByName(planItemInstances, "Task B"));

            assertThat(cmmnRuntimeService.createPlanItemInstanceQuery().count()).isZero();
            assertThat(cmmnRuntimeService.createCaseInstanceQuery().count()).isZero();
            assertCaseInstanceEnded(caseInstance);
        } finally {
            Authentication.setAuthenticatedUserId(previousUserId);
        }
    }

    @Test
    @CmmnDeployment(resources = "org/flowable/cmmn/test/reactivation/Simple_Reactivation_Test_Case.cmmn.xml")
    public void simpleCaseReactivationTest() {
        String previousUserId = Authentication.getAuthenticatedUserId();
        try {
            Authentication.setAuthenticatedUserId("JohnDoe");
            final HistoricCaseInstance caze = createAndFinishSimpleCase("simpleReactivationTestCase");

            CaseInstance reactivatedCaze = cmmnHistoryService.reactivateHistoricCaseInstance(caze.getId(), null);
            assertThat(reactivatedCaze).isNotNull();

            List<PlanItemInstance> planItemInstances = getAllPlanItemInstances(reactivatedCaze.getId());
            assertThat(planItemInstances).isNotNull().hasSize(6);
            assertPlanItemInstanceState(planItemInstances, "Task C", ACTIVE);
            assertCaseInstanceNotEnded(reactivatedCaze);

            // the plan items must be equal for both the runtime as well as the history as of now
            assertSamePlanItemState(caze, reactivatedCaze);

            // make sure we have exactly the same variables as the historic case
            assertSameVariables(caze, reactivatedCaze);
        } finally {
            Authentication.setAuthenticatedUserId(previousUserId);
        }
    }

    @Test
    @CmmnDeployment(resources = "org/flowable/cmmn/test/reactivation/Simple_Reactivation_Test_Case.cmmn.xml")
    public void simpleCaseReactivationHistoryTest() {
        String previousUserId = Authentication.getAuthenticatedUserId();
        try {
            Authentication.setAuthenticatedUserId("JohnDoe");
            final HistoricCaseInstance caze = createAndFinishSimpleCase("simpleReactivationTestCase");

            CaseInstance reactivatedCaze = cmmnHistoryService.reactivateHistoricCaseInstance(caze.getId(), null);
            assertThat(reactivatedCaze).isNotNull();

            HistoricCaseInstance historicCaseInstance = cmmnHistoryService.createHistoricCaseInstanceQuery().caseInstanceId(caze.getId()).singleResult();
            assertThat(historicCaseInstance).isNotNull();
            assertThat(historicCaseInstance.getState()).isEqualTo(reactivatedCaze.getState());
            assertThat(historicCaseInstance.getEndTime()).isNull();
        } finally {
            Authentication.setAuthenticatedUserId(previousUserId);
        }
    }

    protected HistoricCaseInstance createAndFinishSimpleCase(String caseDefinitionKey) {
        CaseInstance caseInstance = cmmnRuntimeService.createCaseInstanceBuilder()
            .caseDefinitionKey(caseDefinitionKey)
            .variable("foo", "fooValue")
            .variable("bar", "barValue")
            .start();

        List<PlanItemInstance> planItemInstances = getPlanItemInstances(caseInstance.getId());
        assertPlanItemInstanceState(planItemInstances, "Task A", ACTIVE);
        cmmnRuntimeService.triggerPlanItemInstance(getPlanItemInstanceIdByName(planItemInstances, "Task A"));

        planItemInstances = getPlanItemInstances(caseInstance.getId());
        assertPlanItemInstanceState(planItemInstances, "Task B", ACTIVE);
        cmmnRuntimeService.triggerPlanItemInstance(getPlanItemInstanceIdByName(planItemInstances, "Task B"));

        return cmmnHistoryService.createHistoricCaseInstanceQuery().finished().singleResult();
    }

    protected void assertSameVariables(HistoricCaseInstance c1, CaseInstance c2) {
        List<HistoricVariableInstance> originalVars = cmmnEngineConfiguration.getCmmnHistoryService().createHistoricVariableInstanceQuery()
            .caseInstanceId(c1.getId())
            .list();

        Map<String, VariableInstance> reactivatedVars = cmmnEngineConfiguration.getCmmnRuntimeService().getVariableInstances(c2.getId());

        for (HistoricVariableInstance originalVar : originalVars) {
            VariableInstance reactivatedVar = reactivatedVars.remove(originalVar.getVariableName());
            assertThat(reactivatedVar).isNotNull();
            assertThat(reactivatedVar.getValue()).isEqualTo(originalVar.getValue());
        }

        assertThat(reactivatedVars).hasSize(0);
    }

    protected void assertSamePlanItemState(HistoricCaseInstance c1, CaseInstance c2) {
        List<PlanItemInstance> runtimePlanItems = getAllPlanItemInstances(c2.getId());
        List<HistoricPlanItemInstance> historicPlanItems = cmmnHistoryService.createHistoricPlanItemInstanceQuery().planItemInstanceCaseInstanceId(c1.getId()).list();

        assertThat(runtimePlanItems).isNotNull();
        assertThat(historicPlanItems).isNotNull();
        assertThat(runtimePlanItems).hasSize(historicPlanItems.size());

        Map<String, HistoricPlanItemInstance> historyMap = new HashMap<>(historicPlanItems.size());
        for (HistoricPlanItemInstance historicPlanItem : historicPlanItems) {
            historyMap.put(historicPlanItem.getId(), historicPlanItem);
        }

        for (PlanItemInstance runtimePlanItem : runtimePlanItems) {
            HistoricPlanItemInstance historicPlanItemInstance = historyMap.remove(runtimePlanItem.getId());
            assertThat(historicPlanItemInstance).isNotNull();
            assertThat(runtimePlanItem.getState()).isEqualTo(historicPlanItemInstance.getState());
        }

        assertThat(historyMap).hasSize(0);
    }
}
