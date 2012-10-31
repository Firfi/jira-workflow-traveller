package ru.megaplan.jira.plugins.workflow.util.traveller;

import com.atlassian.crowd.embedded.api.User;
import com.atlassian.jira.bc.issue.IssueService;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.status.Status;
import com.atlassian.jira.util.ErrorCollection;
import com.atlassian.jira.util.collect.MultiMap;

import java.util.List;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: firfi
 * Date: 19.06.12
 * Time: 12:43
 * To change this template use File | Settings | File Templates.
 */
public interface WorkflowTraveller {
    List<IssueService.IssueResult> travel(User user, Issue issue, Status to, MultiMap<String, Object, ? extends List> params, List<IssueService.TransitionValidationResult> errorResults);
    List<IssueService.IssueResult> travel(User user, Issue issue, Status to, Map<String, Object> params, List<IssueService.TransitionValidationResult> errorResults);
    List<IssueService.IssueResult> travel(User user, Issue issue, Status to, List<IssueService.TransitionValidationResult> errorResults);
}
