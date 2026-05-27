package com.personalenglishai.backend.dto.admin;

public class AdminDataCleaningOverviewResponse {
    private long sourceCount;
    private long jobCount;
    private long completedJobCount;
    private long failedJobCount;
    private long runningJobCount;

    public long getSourceCount() { return sourceCount; }
    public void setSourceCount(long sourceCount) { this.sourceCount = sourceCount; }
    public long getJobCount() { return jobCount; }
    public void setJobCount(long jobCount) { this.jobCount = jobCount; }
    public long getCompletedJobCount() { return completedJobCount; }
    public void setCompletedJobCount(long completedJobCount) { this.completedJobCount = completedJobCount; }
    public long getFailedJobCount() { return failedJobCount; }
    public void setFailedJobCount(long failedJobCount) { this.failedJobCount = failedJobCount; }
    public long getRunningJobCount() { return runningJobCount; }
    public void setRunningJobCount(long runningJobCount) { this.runningJobCount = runningJobCount; }
}
