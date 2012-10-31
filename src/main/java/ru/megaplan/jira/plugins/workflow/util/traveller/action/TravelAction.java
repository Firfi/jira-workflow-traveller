package ru.megaplan.jira.plugins.workflow.util.traveller.action;

import com.atlassian.crowd.embedded.api.User;
import com.atlassian.jira.bc.issue.IssueService;
import com.atlassian.jira.config.StatusManager;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.IssueManager;
import com.atlassian.jira.issue.status.Status;
import com.atlassian.jira.user.util.UserManager;
import com.atlassian.jira.web.action.JiraWebActionSupport;
import ru.megaplan.jira.plugins.workflow.util.traveller.WorkflowTraveller;

import java.util.*;

/**
 * Created with IntelliJ IDEA.
 * User: firfi
 * Date: 19.06.12
 * Time: 15:40
 * To change this template use File | Settings | File Templates.
 */
public class TravelAction extends JiraWebActionSupport {
    private String to;
    private String user;
    private String issueKey;
    private String somefieldName;
    private String somefieldValue;

    private List<IssueService.IssueResult> validResults;
    private List<IssueService.TransitionValidationResult> errorResults;

    private final WorkflowTraveller workflowTraveller;
    private final UserManager userManager;
    private final IssueManager issueManager;
    private final StatusManager statusManager;

    TravelAction(WorkflowTraveller workflowTraveller, UserManager userManager, IssueManager issueManager, StatusManager statusManager) {
        this.workflowTraveller = workflowTraveller;
        this.userManager = userManager;
        this.issueManager = issueManager;
        this.statusManager = statusManager;
    }

    @Override
    public String doDefault() throws Exception {
        return INPUT;
    }

    @Override
    public String doExecute() throws Exception {
        return doDefault();
    }

    public String doTravel() {
        if (!isSystemAdministrator()) {
            addErrorMessage("can be executed only by administrators");
            return ERROR;
        }
        if (to == null || user == null || issueKey == null ||
                to.isEmpty() || user.isEmpty() || issueKey.isEmpty()
                ) {
            addErrorMessage("ERROR fill all fields please");
            return ERROR;
        }
        User u = userManager.getUser(user);
        log.error("to: " + to);
        Status s = statusManager.getStatus(to);
        Issue i = issueManager.getIssueObject(issueKey);
        if (u == null || s == null || i == null) {
            addErrorMessage("some of fields is not valid : " + "user : " + u + " status : " + s + " issue : " + i);
            return ERROR;
        }
        errorResults = new ArrayList<IssueService.TransitionValidationResult>();
        Map<String, Object> params = new HashMap<String, Object>();
        if (somefieldName != null && somefieldValue != null) {
            params.put(somefieldName, somefieldValue);
            log.debug("adding params : " + somefieldName + " : " + somefieldValue);
        }
        try {
            validResults = workflowTraveller.travel(u,i,s,params,errorResults);
        } catch(Exception e) {
            addErrorMessage("caught exception : " + e.getClass());
            addErrorMessage(e.getMessage());
            addErrorMessage(Arrays.toString(e.getStackTrace()));
            return ERROR;
        }

        return SUCCESS;
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getIssueKey() {
        return issueKey;
    }

    public void setIssueKey(String issueKey) {
        this.issueKey = issueKey;
    }

    public List<IssueService.IssueResult> getValidResults() {
        return validResults;
    }

    public List<IssueService.TransitionValidationResult> getErrorResults() {
        return errorResults;
    }

    public String getSomefieldName() {
        return somefieldName;
    }

    public void setSomefieldName(String somefieldName) {
        this.somefieldName = somefieldName;
    }

    public String getSomefieldValue() {
        return somefieldValue;
    }

    public void setSomefieldValue(String somefieldValue) {
        this.somefieldValue = somefieldValue;
    }
}
