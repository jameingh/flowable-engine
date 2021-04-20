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
package org.flowable.engine.impl;

import org.flowable.common.engine.api.FlowableIllegalArgumentException;
import org.flowable.common.engine.impl.interceptor.EngineConfigurationConstants;
import org.flowable.common.engine.impl.service.CommonEngineServiceImpl;
import org.flowable.engine.TaskService;
import org.flowable.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.flowable.engine.impl.cmd.*;
import org.flowable.engine.impl.persistence.entity.CommentEntity;
import org.flowable.engine.runtime.DataObject;
import org.flowable.engine.task.Attachment;
import org.flowable.engine.task.Comment;
import org.flowable.engine.task.Event;
import org.flowable.form.api.FormInfo;
import org.flowable.identitylink.api.IdentityLink;
import org.flowable.identitylink.api.IdentityLinkType;
import org.flowable.idm.api.IdmEngineConfigurationApi;
import org.flowable.idm.api.IdmIdentityService;
import org.flowable.task.api.*;
import org.flowable.task.service.impl.NativeTaskQueryImpl;
import org.flowable.task.service.impl.TaskQueryImpl;
import org.flowable.variable.api.persistence.entity.VariableInstance;

import java.io.InputStream;
import java.util.*;

/**
 * @author Tom Baeyens
 * @author Joram Barrez
 */
public class TaskServiceImpl extends CommonEngineServiceImpl<ProcessEngineConfigurationImpl> implements TaskService {

    public TaskServiceImpl(ProcessEngineConfigurationImpl processEngineConfiguration) {
        super(processEngineConfiguration);
    }

    @Override
    public Task newTask() {
        return newTask(null);
    }

    @Override
    public Task newTask(String taskId) {
        return commandExecutor.execute(new NewTaskCmd(taskId));
    }

    @Override
    public void saveTask(Task task) {
        commandExecutor.execute(new SaveTaskCmd(task));
    }

    @Override
    public void deleteTask(String taskId) {
        commandExecutor.execute(new DeleteTaskCmd(taskId, null, false));
    }

    @Override
    public void deleteTasks(Collection<String> taskIds) {
        commandExecutor.execute(new DeleteTaskCmd(taskIds, null, false));
    }

    @Override
    public void deleteTask(String taskId, boolean cascade) {
        commandExecutor.execute(new DeleteTaskCmd(taskId, null, cascade));
    }

    @Override
    public void deleteTasks(Collection<String> taskIds, boolean cascade) {
        commandExecutor.execute(new DeleteTaskCmd(taskIds, null, cascade));
    }

    @Override
    public void deleteTask(String taskId, String deleteReason) {
        commandExecutor.execute(new DeleteTaskCmd(taskId, deleteReason, false));
    }

    @Override
    public void deleteTasks(Collection<String> taskIds, String deleteReason) {
        commandExecutor.execute(new DeleteTaskCmd(taskIds, deleteReason, false));
    }

    @Override
    public void setAssignee(String taskId, String userId) {
        commandExecutor.execute(new AddIdentityLinkCmd(taskId, userId, AddIdentityLinkCmd.IDENTITY_USER, IdentityLinkType.ASSIGNEE));
    }

    @Override
    public void setOwner(String taskId, String userId) {
        commandExecutor.execute(new AddIdentityLinkCmd(taskId, userId, AddIdentityLinkCmd.IDENTITY_USER, IdentityLinkType.OWNER));
    }

    @Override
    public void addCandidateUser(String taskId, String userId) {
        commandExecutor.execute(new AddIdentityLinkCmd(taskId, userId, AddIdentityLinkCmd.IDENTITY_USER, IdentityLinkType.CANDIDATE));
    }

    @Override
    public void addCandidateGroup(String taskId, String groupId) {
        commandExecutor.execute(new AddIdentityLinkCmd(taskId, groupId, AddIdentityLinkCmd.IDENTITY_GROUP, IdentityLinkType.CANDIDATE));
    }

    @Override
    public void addUserIdentityLink(String taskId, String userId, String identityLinkType) {
        commandExecutor.execute(new AddIdentityLinkCmd(taskId, userId, AddIdentityLinkCmd.IDENTITY_USER, identityLinkType));
    }

    @Override
    public void addGroupIdentityLink(String taskId, String groupId, String identityLinkType) {
        commandExecutor.execute(new AddIdentityLinkCmd(taskId, groupId, AddIdentityLinkCmd.IDENTITY_GROUP, identityLinkType));
    }

    @Override
    public void deleteCandidateGroup(String taskId, String groupId) {
        commandExecutor.execute(new DeleteIdentityLinkCmd(taskId, null, groupId, IdentityLinkType.CANDIDATE));
    }

    @Override
    public void deleteCandidateUser(String taskId, String userId) {
        commandExecutor.execute(new DeleteIdentityLinkCmd(taskId, userId, null, IdentityLinkType.CANDIDATE));
    }

    @Override
    public void deleteGroupIdentityLink(String taskId, String groupId, String identityLinkType) {
        commandExecutor.execute(new DeleteIdentityLinkCmd(taskId, null, groupId, identityLinkType));
    }

    @Override
    public void deleteUserIdentityLink(String taskId, String userId, String identityLinkType) {
        commandExecutor.execute(new DeleteIdentityLinkCmd(taskId, userId, null, identityLinkType));
    }

    @Override
    public List<IdentityLink> getIdentityLinksForTask(String taskId) {
        return commandExecutor.execute(new GetIdentityLinksForTaskCmd(taskId));
    }

    @Override
    public void claim(String taskId, String userId) {
        commandExecutor.execute(new ClaimTaskCmd(taskId, userId));
    }

    @Override
    public void unclaim(String taskId) {
        commandExecutor.execute(new ClaimTaskCmd(taskId, null));
    }

    @Override
    public void complete(String taskId) {
        commandExecutor.execute(new CompleteTaskCmd(taskId, (Map<String, Object>) null));
    }

    @Override
    public void complete(String taskId, Map<String, Object> variables) {
        commandExecutor.execute(new CompleteTaskCmd(taskId, variables));
    }

    @Override
    public void complete(String taskId, Map<String, Object> variables, Map<String, Object> transientVariables) {
        commandExecutor.execute(new CompleteTaskCmd(taskId, variables, transientVariables));
    }

    @Override
    public void complete(String taskId, Map<String, Object> variables, boolean localScope) {
        commandExecutor.execute(new CompleteTaskCmd(taskId, variables, localScope));
    }

    @Override
    public void completeTaskWithForm(String taskId, String formDefinitionId, String outcome, Map<String, Object> variables) {
        commandExecutor.execute(new CompleteTaskWithFormCmd(taskId, formDefinitionId, outcome, variables));
    }

    @Override
    public void completeTaskWithForm(String taskId, String formDefinitionId, String outcome,
            Map<String, Object> variables, Map<String, Object> transientVariables) {

        commandExecutor.execute(new CompleteTaskWithFormCmd(taskId, formDefinitionId, outcome, variables, transientVariables));
    }
    @Override
    public void completeTaskWithForm(String taskId, String formDefinitionId, String outcome,
            Map<String, Object> variables, boolean localScope) {

        commandExecutor.execute(new CompleteTaskWithFormCmd(taskId, formDefinitionId, outcome, variables, localScope));
    }

    @Override
    public FormInfo getTaskFormModel(String taskId) {
        return commandExecutor.execute(new GetTaskFormModelCmd(taskId, false));
    }
    
    @Override
    public FormInfo getTaskFormModel(String taskId, boolean ignoreVariables) {
        return commandExecutor.execute(new GetTaskFormModelCmd(taskId, ignoreVariables));
    }

    @Override
    public void delegateTask(String taskId, String userId) {
        commandExecutor.execute(new DelegateTaskCmd(taskId, userId));
    }

    @Override
    public void resolveTask(String taskId) {
        commandExecutor.execute(new ResolveTaskCmd(taskId, null));
    }

    @Override
    public void resolveTask(String taskId, Map<String, Object> variables) {
        commandExecutor.execute(new ResolveTaskCmd(taskId, variables));
    }

    @Override
    public void resolveTask(String taskId, Map<String, Object> variables, Map<String, Object> transientVariables) {
        commandExecutor.execute(new ResolveTaskCmd(taskId, variables, transientVariables));
    }

    @Override
    public void setPriority(String taskId, int priority) {
        commandExecutor.execute(new SetTaskPriorityCmd(taskId, priority));
    }

    @Override
    public void setDueDate(String taskId, Date dueDate) {
        commandExecutor.execute(new SetTaskDueDateCmd(taskId, dueDate));
    }

    /**
     * 返回的信息有：数据库类型、taskService配置（包括数据库操作实现类）、变量服务配置（包括数据库操作实现类）
     * @return
     */
    @Override
    public TaskQuery createTaskQuery() {
        //通过ProcessEngineConfigurationImpl 获取 数据库类型、taskService配置、变量服务配置。对外包装成一个TaskQueryImpl类。
        //具体的数据库类型、taskService配置、变量服务配置是什么样的？看一看ProcessEngineConfigurationImpl类和参考AbstractEngineConfiguration类
        // 🌈 数据库类型：内部的Properties对象预先保存了各种支持的数据，从datasource中获取connection，再从连接里获取数据库类型
        //   PooledDataSource 数据源的初始化就是拿的配置文件里配置的数据库信息初始化的，参考AbstractEngineConfiguration.initDataSource()里的下面这段代码
        //   PooledDataSource pooledDataSource = new PooledDataSource(this.getClass().getClassLoader(), jdbcDriver, jdbcUrl, jdbcUsername, jdbcPassword);
        // 🌈 taskService配置：在初始化流程引擎的时候初始化的，同时也会初始化设置一些数据库操作实现类
        return new TaskQueryImpl(commandExecutor, configuration.getDatabaseType(), configuration.getTaskServiceConfiguration(), 
                configuration.getVariableServiceConfiguration(), getIdmIdentityService());
    }

    @Override
    public NativeTaskQuery createNativeTaskQuery() {
        return new NativeTaskQueryImpl(commandExecutor, configuration.getTaskServiceConfiguration());
    }

    @Override
    public Map<String, Object> getVariables(String taskId) {
        return commandExecutor.execute(new GetTaskVariablesCmd(taskId, null, false));
    }

    @Override
    public Map<String, Object> getVariablesLocal(String taskId) {
        return commandExecutor.execute(new GetTaskVariablesCmd(taskId, null, true));
    }

    @Override
    public Map<String, Object> getVariables(String taskId, Collection<String> variableNames) {
        return commandExecutor.execute(new GetTaskVariablesCmd(taskId, variableNames, false));
    }

    @Override
    public Map<String, Object> getVariablesLocal(String taskId, Collection<String> variableNames) {
        return commandExecutor.execute(new GetTaskVariablesCmd(taskId, variableNames, true));
    }

    @Override
    public Object getVariable(String taskId, String variableName) {
        return commandExecutor.execute(new GetTaskVariableCmd(taskId, variableName, false));
    }

    @Override
    public <T> T getVariable(String taskId, String variableName, Class<T> variableClass) {
        return variableClass.cast(getVariable(taskId, variableName));
    }

    @Override
    public boolean hasVariable(String taskId, String variableName) {
        return commandExecutor.execute(new HasTaskVariableCmd(taskId, variableName, false));
    }

    @Override
    public Object getVariableLocal(String taskId, String variableName) {
        return commandExecutor.execute(new GetTaskVariableCmd(taskId, variableName, true));
    }

    @Override
    public <T> T getVariableLocal(String taskId, String variableName, Class<T> variableClass) {
        return variableClass.cast(getVariableLocal(taskId, variableName));
    }

    @Override
    public List<VariableInstance> getVariableInstancesLocalByTaskIds(Set<String> taskIds) {
        return commandExecutor.execute(new GetTasksLocalVariablesCmd(taskIds));
    }

    @Override
    public boolean hasVariableLocal(String taskId, String variableName) {
        return commandExecutor.execute(new HasTaskVariableCmd(taskId, variableName, true));
    }

    @Override
    public void setVariable(String taskId, String variableName, Object value) {
        if (variableName == null) {
            throw new FlowableIllegalArgumentException("variableName is null");
        }
        Map<String, Object> variables = new HashMap<>();
        variables.put(variableName, value);
        commandExecutor.execute(new SetTaskVariablesCmd(taskId, variables, false));
    }

    @Override
    public void setVariableLocal(String taskId, String variableName, Object value) {
        if (variableName == null) {
            throw new FlowableIllegalArgumentException("variableName is null");
        }
        Map<String, Object> variables = new HashMap<>();
        variables.put(variableName, value);
        commandExecutor.execute(new SetTaskVariablesCmd(taskId, variables, true));
    }

    @Override
    public void setVariables(String taskId, Map<String, ? extends Object> variables) {
        commandExecutor.execute(new SetTaskVariablesCmd(taskId, variables, false));
    }

    @Override
    public void setVariablesLocal(String taskId, Map<String, ? extends Object> variables) {
        commandExecutor.execute(new SetTaskVariablesCmd(taskId, variables, true));
    }

    @Override
    public void removeVariable(String taskId, String variableName) {
        Collection<String> variableNames = new ArrayList<>();
        variableNames.add(variableName);
        commandExecutor.execute(new RemoveTaskVariablesCmd(taskId, variableNames, false));
    }

    @Override
    public void removeVariableLocal(String taskId, String variableName) {
        Collection<String> variableNames = new ArrayList<>(1);
        variableNames.add(variableName);
        commandExecutor.execute(new RemoveTaskVariablesCmd(taskId, variableNames, true));
    }

    @Override
    public void removeVariables(String taskId, Collection<String> variableNames) {
        commandExecutor.execute(new RemoveTaskVariablesCmd(taskId, variableNames, false));
    }

    @Override
    public void removeVariablesLocal(String taskId, Collection<String> variableNames) {
        commandExecutor.execute(new RemoveTaskVariablesCmd(taskId, variableNames, true));
    }

    @Override
    public Comment addComment(String taskId, String processInstance, String message) {
        return commandExecutor.execute(new AddCommentCmd(taskId, processInstance, message));
    }

    @Override
    public Comment addComment(String taskId, String processInstance, String type, String message) {
        return commandExecutor.execute(new AddCommentCmd(taskId, processInstance, type, message));
    }
    
    @Override
    public void saveComment(Comment comment) {
        commandExecutor.execute(new SaveCommentCmd((CommentEntity) comment));
    }

    @Override
    public Comment getComment(String commentId) {
        return commandExecutor.execute(new GetCommentCmd(commentId));
    }

    @Override
    public Event getEvent(String eventId) {
        return commandExecutor.execute(new GetTaskEventCmd(eventId));
    }

    @Override
    public List<Comment> getTaskComments(String taskId) {
        return commandExecutor.execute(new GetTaskCommentsCmd(taskId));
    }

    @Override
    public List<Comment> getTaskComments(String taskId, String type) {
        return commandExecutor.execute(new GetTaskCommentsByTypeCmd(taskId, type));
    }

    @Override
    public List<Comment> getCommentsByType(String type) {
        return commandExecutor.execute(new GetTypeCommentsCmd(type));
    }

    @Override
    public List<Event> getTaskEvents(String taskId) {
        return commandExecutor.execute(new GetTaskEventsCmd(taskId));
    }

    @Override
    public List<Comment> getProcessInstanceComments(String processInstanceId) {
        return commandExecutor.execute(new GetProcessInstanceCommentsCmd(processInstanceId));
    }

    @Override
    public List<Comment> getProcessInstanceComments(String processInstanceId, String type) {
        return commandExecutor.execute(new GetProcessInstanceCommentsCmd(processInstanceId, type));
    }

    @Override
    public Attachment createAttachment(String attachmentType, String taskId, String processInstanceId, String attachmentName, String attachmentDescription, InputStream content) {
        return commandExecutor.execute(new CreateAttachmentCmd(attachmentType, taskId, processInstanceId, attachmentName, attachmentDescription, content, null));
    }

    @Override
    public Attachment createAttachment(String attachmentType, String taskId, String processInstanceId, String attachmentName, String attachmentDescription, String url) {
        return commandExecutor.execute(new CreateAttachmentCmd(attachmentType, taskId, processInstanceId, attachmentName, attachmentDescription, null, url));
    }

    @Override
    public InputStream getAttachmentContent(String attachmentId) {
        return commandExecutor.execute(new GetAttachmentContentCmd(attachmentId));
    }

    @Override
    public void deleteAttachment(String attachmentId) {
        commandExecutor.execute(new DeleteAttachmentCmd(attachmentId));
    }

    @Override
    public void deleteComments(String taskId, String processInstanceId) {
        commandExecutor.execute(new DeleteCommentCmd(taskId, processInstanceId, null));
    }

    @Override
    public void deleteComment(String commentId) {
        commandExecutor.execute(new DeleteCommentCmd(null, null, commentId));
    }

    @Override
    public Attachment getAttachment(String attachmentId) {
        return commandExecutor.execute(new GetAttachmentCmd(attachmentId));
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Attachment> getTaskAttachments(String taskId) {
        return (List<Attachment>) commandExecutor.execute(new GetTaskAttachmentsCmd(taskId));
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Attachment> getProcessInstanceAttachments(String processInstanceId) {
        return (List<Attachment>) commandExecutor.execute(new GetProcessInstanceAttachmentsCmd(processInstanceId));
    }

    @Override
    public void saveAttachment(Attachment attachment) {
        commandExecutor.execute(new SaveAttachmentCmd(attachment));
    }

    @Override
    public List<Task> getSubTasks(String parentTaskId) {
        return commandExecutor.execute(new GetSubTasksCmd(parentTaskId));
    }

    @Override
    public VariableInstance getVariableInstance(String taskId, String variableName) {
        return commandExecutor.execute(new GetTaskVariableInstanceCmd(taskId, variableName, false));
    }

    @Override
    public VariableInstance getVariableInstanceLocal(String taskId, String variableName) {
        return commandExecutor.execute(new GetTaskVariableInstanceCmd(taskId, variableName, true));
    }

    @Override
    public Map<String, VariableInstance> getVariableInstances(String taskId) {
        return commandExecutor.execute(new GetTaskVariableInstancesCmd(taskId, null, false));
    }

    @Override
    public Map<String, VariableInstance> getVariableInstances(String taskId, Collection<String> variableNames) {
        return commandExecutor.execute(new GetTaskVariableInstancesCmd(taskId, variableNames, false));
    }

    @Override
    public Map<String, VariableInstance> getVariableInstancesLocal(String taskId) {
        return commandExecutor.execute(new GetTaskVariableInstancesCmd(taskId, null, true));
    }

    @Override
    public Map<String, VariableInstance> getVariableInstancesLocal(String taskId, Collection<String> variableNames) {
        return commandExecutor.execute(new GetTaskVariableInstancesCmd(taskId, variableNames, true));
    }

    @Override
    public Map<String, DataObject> getDataObjects(String taskId) {
        return commandExecutor.execute(new GetTaskDataObjectsCmd(taskId, null));
    }

    @Override
    public Map<String, DataObject> getDataObjects(String taskId, String locale, boolean withLocalizationFallback) {
        return commandExecutor.execute(new GetTaskDataObjectsCmd(taskId, null, locale, withLocalizationFallback));
    }

    @Override
    public Map<String, DataObject> getDataObjects(String taskId, Collection<String> dataObjectNames) {
        return commandExecutor.execute(new GetTaskDataObjectsCmd(taskId, dataObjectNames));
    }

    @Override
    public Map<String, DataObject> getDataObjects(String taskId, Collection<String> dataObjectNames, String locale, boolean withLocalizationFallback) {
        return commandExecutor.execute(new GetTaskDataObjectsCmd(taskId, dataObjectNames, locale, withLocalizationFallback));
    }

    @Override
    public DataObject getDataObject(String taskId, String dataObject) {
        return commandExecutor.execute(new GetTaskDataObjectCmd(taskId, dataObject));
    }

    @Override
    public DataObject getDataObject(String taskId, String dataObjectName, String locale, boolean withLocalizationFallback) {
        return commandExecutor.execute(new GetTaskDataObjectCmd(taskId, dataObjectName, locale, withLocalizationFallback));
    }

    @Override
    public TaskBuilder createTaskBuilder() {
        return new TaskBuilderImpl(commandExecutor);
    }
    
    protected IdmIdentityService getIdmIdentityService() {
        IdmEngineConfigurationApi idmEngineConfiguration = (IdmEngineConfigurationApi) configuration.getEngineConfigurations()
                .get(EngineConfigurationConstants.KEY_IDM_ENGINE_CONFIG);
        IdmIdentityService idmIdentityService = null;
        if (idmEngineConfiguration != null) {
            idmIdentityService = idmEngineConfiguration.getIdmIdentityService();
        }
        
        return idmIdentityService;
    }

    @Override
    public TaskCompletionBuilder createTaskCompletionBuilder() {
        return new TaskCompletionBuilderImpl(commandExecutor);
    }

}
