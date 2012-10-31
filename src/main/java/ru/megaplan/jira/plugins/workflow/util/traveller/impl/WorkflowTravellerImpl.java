package ru.megaplan.jira.plugins.workflow.util.traveller.impl;

import com.atlassian.crowd.embedded.api.User;
import com.atlassian.jira.bc.issue.IssueService;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.config.StatusManager;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.IssueInputParameters;
import com.atlassian.jira.issue.MutableIssue;
import com.atlassian.jira.issue.status.Status;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.util.collect.MultiMap;
import com.atlassian.jira.util.collect.MultiMaps;
import com.atlassian.jira.workflow.IssueWorkflowManager;
import com.atlassian.jira.workflow.JiraWorkflow;
import com.atlassian.jira.workflow.WorkflowManager;
import com.atlassian.jira.workflow.WorkflowSchemeManager;
import com.atlassian.plugin.web.descriptors.ConditionalDescriptor;
import com.google.common.collect.Sets;
import com.opensymphony.workflow.Workflow;
import com.opensymphony.workflow.loader.ActionDescriptor;
import com.opensymphony.workflow.loader.ConditionalResultDescriptor;
import com.opensymphony.workflow.loader.ResultDescriptor;
import com.opensymphony.workflow.loader.StepDescriptor;
import org.apache.log4j.Logger;
import org.ofbiz.core.entity.GenericValue;
import ru.megaplan.jira.plugins.workflow.util.traveller.WorkflowTraveller;

import java.awt.event.ComponentAdapter;
import java.util.*;

/**
 * Created with IntelliJ IDEA.
 * User: firfi
 * Date: 19.06.12
 * Time: 12:43
 * To change this template use File | Settings | File Templates.
 */
public class WorkflowTravellerImpl implements WorkflowTraveller {

    private final static Logger log = Logger.getLogger(WorkflowTravellerImpl.class);

    private final IssueService issueService;
    private final IssueWorkflowManager issueWorkflowManager;
    private final WorkflowManager workflowManager;
    private final StatusManager statusManager;
    private final JiraAuthenticationContext jiraAuthenticationContext;


    WorkflowTravellerImpl(IssueService issueService,
                          IssueWorkflowManager issueWorkflowManager,
                          WorkflowManager workflowManager,
                          StatusManager statusManager, JiraAuthenticationContext jiraAuthenticationContext) {
        this.issueService = issueService;
        this.issueWorkflowManager = issueWorkflowManager;
        this.workflowManager = workflowManager;
        this.statusManager = statusManager;
        this.jiraAuthenticationContext = jiraAuthenticationContext;
    }

    @Override
    public List<IssueService.IssueResult> travel(User user,
                                                          Issue issue,
                                                          Status to, MultiMap<String, Object, ? extends List> params,
                                                          final List<IssueService.TransitionValidationResult> errorResults) {

        if (user == null) throw new IllegalArgumentException("user is null, gimme some body");
        if (issue == null) throw new IllegalArgumentException("issue is null");
        log.debug("issue : " + issue.getKey());
        if (to == null) throw new IllegalArgumentException("to status is null");
        if (params == null) params = MultiMaps.createListMultiMap();
        if (checkForNullValues(params)) throw new IllegalArgumentException("I do not accept null values in param map");
        if (to.equals(issue.getStatusObject())) {
            log.error("to and from are equals");
            throw new IllegalArgumentException("to and from are equals");
        }
        log.debug("travel start... args : " + user.getName() + " to : " + to.getName() + "; issue :  " + issue.getKey() + " params length : " + params.size());
        Set<Status> availableWorkflowStatuses = getAllAvailableWorkflowStatuses(issue);
        if (!availableWorkflowStatuses.contains(to))
            throw new IllegalArgumentException("passed status : " + to.getName() + " does not exist in available statuses here or even not exist in issue workflow");
        List<IssueService.IssueResult> validResults = simpleTravel(user, issue, to, params, errorResults);
        //TODO:add not simple travel
        return validResults;
    }

    private boolean checkForNullValues(MultiMap<String, Object, ? extends List> params) {
        for (List list : params.values()) {
            if (list == null) return true;
            for (Object o : list) {
                if (o == null) return true;
            }
        }
        return false;
    }

    @Override
    public List<IssueService.IssueResult> travel(User user, Issue issue, Status to, Map<String, Object> params,
                          final List<IssueService.TransitionValidationResult> errorResults) {
        MultiMap<String, Object, ? extends List> multiParams = createMultiMap(params);
        return travel(user, issue, to, multiParams, errorResults);
    }

    @Override
    public List<IssueService.IssueResult> travel(User user, Issue issue, Status to, List<IssueService.TransitionValidationResult> errorResults) {
        return travel(user, issue, to, MultiMaps.<String, Object>createListMultiMap(), errorResults);
    }

    private MultiMap<String, Object, ? extends List> createMultiMap(Map<String, Object> params) {
        MultiMap<String, Object, ? extends List> result = MultiMaps.createListMultiMap();
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            result.putSingle(entry.getKey(), entry.getValue());
        }
        return result;
    }

    private List<IssueService.IssueResult> simpleTravel(User user,
                                                                 Issue issue,
                                                                 Status to,
                                                                 MultiMap<String, Object, ? extends List> params,
                                                                 final List<IssueService.TransitionValidationResult> errorResults
                                                                 ) {
        List<IssueService.IssueResult> resultList = new ArrayList<IssueService.IssueResult>();
        User oldUser = jiraAuthenticationContext.getLoggedInUser();
        jiraAuthenticationContext.setLoggedInUser(user);
        List<IssueService.TransitionValidationResult> validResults = getValidResults(user, issue, to, params, errorResults);
        if (!validResults.isEmpty()) {
            for (IssueService.TransitionValidationResult validResult : validResults) {

                IssueService.IssueResult result = issueService.transition(user, validResult);

                if (result.getErrorCollection() != null && result.getErrorCollection().hasAnyErrors()) {
                    log.warn("result of transition for issue : " + validResult.getIssue().getKey() + " and transition : " +
                    to.getName() + " is not so valid, something wrong here : " +
                    (result.getErrorCollection().getErrorMessages() != null?Arrays.toString(result.getErrorCollection().getErrorMessages().toArray()):" dunno what"));
                    errorResults.add(validResult);
                } else {
                    resultList.add(result);
                    return resultList;
                }
            }
            //TODO: do something with multiple results
        }
        jiraAuthenticationContext.setLoggedInUser(oldUser);
        return resultList;
    }

    private List<IssueService.TransitionValidationResult> getValidResults(User user, Issue issue, Status to, MultiMap<String, Object, ? extends List> params, List<IssueService.TransitionValidationResult> errorResults) {
        List<IssueService.TransitionValidationResult> result = new ArrayList<IssueService.TransitionValidationResult>();

        List<IssueService.TransitionValidationResult> simpleTransitionResults = getSimpleTransitionResults(user, issue, to, params);
        for (IssueService.TransitionValidationResult transitionValidationResult : simpleTransitionResults) {
            if (transitionValidationResult.isValid()) result.add(transitionValidationResult);
            else errorResults.add(transitionValidationResult);
        }
        //TODO : add not simple

        return result;
    }

    private List<IssueService.TransitionValidationResult> getSimpleTransitionResults(User user, Issue issue, Status to, MultiMap<String, Object, ? extends List> params) {
        List<IssueService.TransitionValidationResult> result = new ArrayList<IssueService.TransitionValidationResult>();
        Map<String, Object> simpleParams = getSimpleMap(params);
        Set<Integer> nextStatusActionIds = getNextStatusActionIds(issue, to);
        if (nextStatusActionIds.size() == 0) throw new IllegalArgumentException("to status is not valid for issue : " + issue.getKey() +
                " and status : " + to.getName());
        if (nextStatusActionIds.size() > 1) log.warn("found more than one actions for next status transition : " + issue.getKey() +
                " and status : " + to.getName());
        for (Integer actionId : nextStatusActionIds) {
            IssueInputParameters issueInputParameters = newIssueInputParameters(simpleParams);
            issueInputParameters.setSkipScreenCheck(true);
            log.debug("traveling to status : " + to.getName() + " with action id : " + actionId + "; user : " + user.getName() +
                    " ; issueInputParameters : " + issueInputParameters + " : " + to.getId() + " and logged in user : " + jiraAuthenticationContext.getLoggedInUser().getName());
            IssueService.TransitionValidationResult transitionValidationResult =
                    issueService.validateTransition(user, issue.getId(), actionId, issueInputParameters);

            log.debug("validation result status : " + transitionValidationResult.getIssue().getStatusObject().getName() + " and action id : " + transitionValidationResult.getActionId());
            result.add(transitionValidationResult);
        }
        return result;
    }

    private IssueInputParameters newIssueInputParameters(Map<String, Object> simpleParams) {
        Map<String, String[]> likeHttp = getLikeHttpInput(simpleParams);
        return issueService.newIssueInputParameters(likeHttp);
    }

    private Map<String, String[]> getLikeHttpInput(Map<String, Object> simpleParams) {
        Map<String, String[]> result = new HashMap<String, String[]>();
        for (Map.Entry<String, Object> entry : simpleParams.entrySet()) {
            String[] likeHttpValue;
            if (entry.getValue() instanceof String[]) likeHttpValue = (String[]) entry.getValue();
            else {
                likeHttpValue = new String[] {entry.getValue().toString()};
            }
            result.put(entry.getKey(), likeHttpValue);
        }
        return result;
    }

    private Map<String, Object> getSimpleMap(MultiMap<String, Object, ? extends List> params) {
        Map<String, Object> result = new HashMap<String, Object>();
        for (Map.Entry<String, ? extends List> entry : params.entrySet()) {
            List values = entry.getValue();
            if (values == null || values.size() != 1) continue;
            String key = entry.getKey();
            Object value = values.get(0);
            result.put(key, value);
        }
        return result;
    }

    private Set<Integer> getNextStatusActionIds(Issue issue, Status to) {
        JiraWorkflow workflow = workflowManager.getWorkflow(issue);
        Set<Integer> result = new HashSet<Integer>();
        for (ActionDescriptor actionDescriptor : getAvailableActions(issue)) {
            Set<ResultDescriptor> results = getAllResults(actionDescriptor); //supposed to be size of 1
            for (ResultDescriptor resultDescriptor : results) {
                Status status = workflow.getLinkedStatusObject(workflow.getDescriptor().getStep(resultDescriptor.getStep()));
                if (status != null && status.equals(to)) {
                    result.add(actionDescriptor.getId());
                }
            }
        }
        return result;
    }


    private Set<Status> getAllAvailableWorkflowStatuses(Issue issue) {
        log.debug("getting available workflow statuses");
        JiraWorkflow workflow =  workflowManager.getWorkflow(issue);
        Set<Status> result = new HashSet<Status>();
        for (ActionDescriptor actionDescriptor : getAvailableActions(issue)) {
            log.debug("available action : " + actionDescriptor.getName());
            Set<ResultDescriptor> results = getAllResults(actionDescriptor);
            if (results.size() > 1)
                log.warn("for some reason issue action : "
                        + actionDescriptor.getName() + " for issue : " + issue.getKey() + " has more than one results");
            for (ResultDescriptor resultDescriptor : results) {
                Status status = workflow.getLinkedStatusObject(workflow.getDescriptor().getStep(resultDescriptor.getStep()));
                result.add(status);
                log.debug("linked status object : " + status.getName());
            }
        }
        log.debug("available statuses size : " + result.size());
        return result;
    }

    private Collection<ActionDescriptor> getAvailableActions(Issue issue) {

        Collection<ActionDescriptor> result = new ArrayList<ActionDescriptor>();
        JiraWorkflow workflow = workflowManager.getWorkflow(issue);
        StepDescriptor step = workflow.getLinkedStep(issue.getStatusObject());
        for (Object o : step.getActions()) {
            ActionDescriptor actionDescriptor = (ActionDescriptor) o;
            result.add(actionDescriptor);
        }
        log.debug("available actions size : " + result.size());
        return result;
    }

    private Set<ResultDescriptor> getAllResults(ActionDescriptor actionDescriptor) {
        //List<ResultDescriptor> results = actionDescriptor.getConditionalResults();
        List<ResultDescriptor> results = new ArrayList<ResultDescriptor>();
        if (actionDescriptor.getConditionalResults() != null && actionDescriptor.getConditionalResults().size() > 0)
            log.error("What conditional result doing here? : " + actionDescriptor.getConditionalResults().get(0));
        ResultDescriptor unconditionalResult = actionDescriptor.getUnconditionalResult();
        if (unconditionalResult != null) {results.add(unconditionalResult);}
        return Sets.newHashSet(results);
    }


}
