package com.personalenglishai.backend.dto.admin;

public class CreateDictionaryDataCleaningSourceRequest {
    private String sourceCode;
    private String displayName;
    private String licenseStatus;
    private String mdxPath;
    private String mddPath;
    private String examplesPath;
    private String coverImagePath;

    public String getSourceCode() { return sourceCode; }
    public void setSourceCode(String sourceCode) { this.sourceCode = sourceCode; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getLicenseStatus() { return licenseStatus; }
    public void setLicenseStatus(String licenseStatus) { this.licenseStatus = licenseStatus; }
    public String getMdxPath() { return mdxPath; }
    public void setMdxPath(String mdxPath) { this.mdxPath = mdxPath; }
    public String getMddPath() { return mddPath; }
    public void setMddPath(String mddPath) { this.mddPath = mddPath; }
    public String getExamplesPath() { return examplesPath; }
    public void setExamplesPath(String examplesPath) { this.examplesPath = examplesPath; }
    public String getCoverImagePath() { return coverImagePath; }
    public void setCoverImagePath(String coverImagePath) { this.coverImagePath = coverImagePath; }
}
