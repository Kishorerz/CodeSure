import { useEffect, useState } from "react";
import "./App.css";

interface Repository {
  id: string;
  name: string;
  repositoryUrl: string;
  provider: string;
  defaultBranch: string;
  createdAt: string;
}

interface Commit {
  id: string;
  sha: string;
  message: string;
  authorName: string;
  committedAt: string;
}

interface Finding {
  id: string;
  ruleId: string;
  message: string;
  severity: "CRITICAL" | "HIGH" | "MEDIUM" | "LOW";
  lineNumber: number;
  analyzer: string;
}

interface PrioritizedFinding {
  id: string;
  priorityRank: number;
  severity: "CRITICAL" | "HIGH" | "MEDIUM" | "LOW";
  ruleId: string;
  message: string;
  filePath: string;
  lineNumber: number;
  commitSha: string;
  commitMessage: string;
  rootCause: string;
  remediation: string;
  criticalityReason: string;
}

interface RiskAssessment {
  repositoryId: string;
  repositoryName: string;
  branchName?: string;
  overallRiskScore: number;
  riskLevel: "CRITICAL" | "HIGH" | "MEDIUM" | "LOW";
  totalFilesChanged: number;
  totalAdditions: number;
  totalDeletions: number;
  totalFindings: number;
  criticalFindings: number;
  highFindings: number;
  mediumFindings: number;
  lowFindings: number;
  keyRiskFactors: string[];
  aiExplanation: string;
  prioritizedFindings?: PrioritizedFinding[];
}

const rawApiBase = import.meta.env.VITE_API_BASE_URL || "";
const API_BASE = rawApiBase.replace(/\/+$/, "");

export default function App() {
  const [backendStatus, setBackendStatus] = useState("Checking...");
  const [repositories, setRepositories] = useState<Repository[]>([]);
  const [selectedRepo, setSelectedRepo] = useState<Repository | null>(null);
  const [repoUrlInput, setRepoUrlInput] = useState("");
  const [loading, setLoading] = useState(false);
  const [loadingCommits, setLoadingCommits] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  // Branch switching state
  const [branches, setBranches] = useState<string[]>([]);
  const [selectedBranch, setSelectedBranch] = useState<string>("main");
  const [loadingBranches, setLoadingBranches] = useState<boolean>(false);

  const [commits, setCommits] = useState<Commit[]>([]);
  const [findings, setFindings] = useState<Finding[]>([]);
  const [riskAssessment, setRiskAssessment] = useState<RiskAssessment | null>(null);
  const [activeTab, setActiveTab] = useState<"priorities" | "commits" | "allFindings">("priorities");

  // Health Check with auto-retry for Render cold starts
  useEffect(() => {
    let timer: ReturnType<typeof setTimeout>;

    const checkHealth = () => {
      fetch(`${API_BASE}/api/health`)
        .then((res) => {
          if (!res.ok) throw new Error("HTTP error " + res.status);
          return res.json();
        })
        .then((data) => {
          if (data && data.status === "UP") {
            setBackendStatus("Connected");
          } else {
            setBackendStatus("Unavailable");
          }
        })
        .catch(() => {
          setBackendStatus("Connecting (waking up backend)...");
          timer = setTimeout(checkHealth, 4000);
        });
    };

    checkHealth();
    return () => clearTimeout(timer);
  }, []);

  // Fetch Repositories
  const loadRepositories = () => {
    fetch(`${API_BASE}/api/repositories`)
      .then((res) => (res.ok ? res.json() : []))
      .then((data: Repository[]) => {
        setRepositories(data);
        if (data.length > 0 && !selectedRepo) {
          selectRepository(data[0]);
        }
      })
      .catch(() => {});
  };

  useEffect(() => {
    loadRepositories();
  }, [backendStatus]);

  const loadBranchData = (repoId: string, branchName: string) => {
    const encodedBranch = encodeURIComponent(branchName);

    // Fetch branch commits
    fetch(`${API_BASE}/api/repositories/${repoId}/commits?branch=${encodedBranch}`)
      .then((res) => (res.ok ? res.json() : []))
      .then((data) => setCommits(data))
      .catch(() => setCommits([]));

    // Fetch branch findings
    fetch(`${API_BASE}/api/repositories/${repoId}/findings?branch=${encodedBranch}`)
      .then((res) => (res.ok ? res.json() : []))
      .then((data) => setFindings(data))
      .catch(() => setFindings([]));

    // Fetch branch risk assessment
    fetch(`${API_BASE}/api/repositories/${repoId}/risk-assessment?branch=${encodedBranch}`)
      .then((res) => (res.ok ? res.json() : null))
      .then((data) => setRiskAssessment(data))
      .catch(() => setRiskAssessment(null));
  };

  const selectRepository = async (repo: Repository, branchToSelect?: string) => {
    setSelectedRepo(repo);
    setErrorMsg(null);

    const initialBranch = branchToSelect || repo.defaultBranch || "main";
    setSelectedBranch(initialBranch);

    // Fetch available branches for this repo
    setLoadingBranches(true);
    let effectiveBranch = initialBranch;
    try {
      const res = await fetch(`${API_BASE}/api/repositories/${repo.id}/branches`);
      if (res.ok) {
        const branchList: string[] = await res.json();
        if (branchList && branchList.length > 0) {
          setBranches(branchList);
          if (!branchList.includes(initialBranch)) {
            effectiveBranch = branchList[0];
            setSelectedBranch(effectiveBranch);
          }
        } else {
          setBranches([initialBranch]);
        }
      } else {
        setBranches([initialBranch]);
      }
    } catch {
      setBranches([initialBranch]);
    } finally {
      setLoadingBranches(false);
    }

    loadBranchData(repo.id, effectiveBranch);
  };

  const handleRegisterRepo = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!repoUrlInput.trim()) return;

    setLoading(true);
    setErrorMsg(null);
    try {
      const res = await fetch(`${API_BASE}/api/repositories/sync-url`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ repositoryUrl: repoUrlInput.trim() }),
      });

      if (!res.ok) {
        let errText = "Failed to register repository";
        try {
          const errJson = await res.json();
          errText = errJson.message || errJson.error || errText;
        } catch {
          errText = await res.text();
        }
        throw new Error(errText);
      }

      const created: Repository = await res.json();
      setRepoUrlInput("");
      loadRepositories();
      selectRepository(created);
    } catch (err: unknown) {
      if (err instanceof Error) {
        setErrorMsg(err.message);
      } else {
        setErrorMsg("An unexpected error occurred");
      }
    } finally {
      setLoading(false);
    }
  };

  const handleSwitchAndAnalyzeBranch = async (targetBranch: string) => {
    if (!selectedRepo) return;
    setSelectedBranch(targetBranch);
    setLoadingCommits(true);
    try {
      const res = await fetch(
        `${API_BASE}/api/repositories/${selectedRepo.id}/sync-commits?branch=${encodeURIComponent(targetBranch)}&limit=30`,
        { method: "POST" }
      );
      if (res.ok) {
        const data: Commit[] = await res.json();
        setCommits(data);
      }
      loadBranchData(selectedRepo.id, targetBranch);
    } catch (err) {
      console.error(err);
    } finally {
      setLoadingCommits(false);
    }
  };

  const getSeverityBadgeClass = (severity: string) => {
    switch (severity) {
      case "CRITICAL":
        return "badge critical";
      case "HIGH":
        return "badge high";
      case "MEDIUM":
        return "badge medium";
      default:
        return "badge low";
    }
  };

  const getPriorityBadgeClass = (rank: number) => {
    if (rank === 1) return "priority-rank rank-1";
    if (rank === 2) return "priority-rank rank-2";
    if (rank <= 4) return "priority-rank rank-3";
    return "priority-rank rank-default";
  };

  const prioritizedList = riskAssessment?.prioritizedFindings || [];

  return (
    <div className="codesure-app">
      <header className="codesure-header">
        <div className="brand">
          <h1>DevSentinel <span className="subtitle">CodeSure Risk Platform</span></h1>
        </div>
        <div className="status-indicators">
          <span className={`pill ${backendStatus === "Connected" ? "online" : "offline"}`}>
            Backend: {backendStatus}
          </span>
          <span className="pill online">Database: PostgreSQL</span>
          <span className="pill badge-pill">Multi-Branch Vulnerability Analysis</span>
        </div>
      </header>

      <div className="codesure-layout">
        {/* Sidebar */}
        <aside className="codesure-sidebar">
          <h3>Connected Repositories</h3>
          <form onSubmit={handleRegisterRepo} className="repo-form">
            <input
              type="text"
              placeholder="https://github.com/owner/repo"
              value={repoUrlInput}
              onChange={(e) => setRepoUrlInput(e.target.value)}
              disabled={loading}
            />
            <button type="submit" disabled={loading || !repoUrlInput.trim()}>
              {loading ? "Analyzing Repo..." : "+ Connect & Analyze Repo"}
            </button>
          </form>

          {errorMsg && <div className="error-alert">{errorMsg}</div>}

          <div className="repo-list">
            {repositories.length === 0 ? (
              <p className="empty-hint">No repositories connected yet. Paste a GitHub repository URL above.</p>
            ) : (
              repositories.map((repo) => (
                <div
                  key={repo.id}
                  className={`repo-card ${selectedRepo?.id === repo.id ? "active" : ""}`}
                  onClick={() => selectRepository(repo)}
                >
                  <h4>{repo.name}</h4>
                  <p className="repo-url">{repo.repositoryUrl}</p>
                  <span className="tag">Default: {repo.defaultBranch}</span>
                </div>
              ))
            )}
          </div>
        </aside>

        {/* Main Content */}
        <main className="codesure-main">
          {selectedRepo ? (
            <>
              {/* Header Actions & Branch Switcher Bar */}
              <div className="repo-header-container">
                <div className="repo-header-main">
                  <div>
                    <h2>{selectedRepo.name}</h2>
                    <a href={selectedRepo.repositoryUrl} target="_blank" rel="noreferrer" className="link">
                      {selectedRepo.repositoryUrl}
                    </a>
                  </div>
                  <div className="action-buttons">
                    <span className="pill online">{commits.length} Commits Analyzed</span>
                    <button
                      onClick={() => handleSwitchAndAnalyzeBranch(selectedBranch)}
                      disabled={loadingCommits}
                      className="btn-secondary"
                    >
                      {loadingCommits ? "Analyzing Branch..." : `↻ Re-Analyze ${selectedBranch}`}
                    </button>
                  </div>
                </div>

                {/* Branch Switcher Controls */}
                <div className="branch-switcher-bar">
                  <div className="branch-selector-group">
                    <span className="branch-icon">🌿</span>
                    <label htmlFor="branch-select" className="branch-label">Switch Branch:</label>
                    <select
                      id="branch-select"
                      className="branch-select"
                      value={selectedBranch}
                      disabled={loadingBranches || loadingCommits}
                      onChange={(e) => handleSwitchAndAnalyzeBranch(e.target.value)}
                    >
                      {branches.map((b) => (
                        <option key={b} value={b}>
                          {b} {b === selectedRepo.defaultBranch ? "(default)" : ""}
                        </option>
                      ))}
                    </select>
                    <button
                      type="button"
                      className="btn-switch-branch"
                      disabled={loadingCommits}
                      onClick={() => handleSwitchAndAnalyzeBranch(selectedBranch)}
                    >
                      {loadingCommits ? "Analyzing..." : "Switch & Analyze Branch"}
                    </button>
                  </div>
                  <div className="branch-info-banner">
                    <span className="branch-badge">Active: {selectedBranch}</span>
                    <span className="branch-hint">
                      Analyzing development stage changes on <strong>{selectedBranch}</strong> before merging into main.
                    </span>
                  </div>
                </div>
              </div>

              {/* Risk Intelligence Card */}
              {riskAssessment && (
                <section className="risk-overview-card">
                  <div className="risk-score-box">
                    <span className="risk-title">BRANCH RISK SCORE</span>
                    <div className="score-value">{riskAssessment.overallRiskScore}</div>
                    <span className={`risk-level-tag ${riskAssessment.riskLevel.toLowerCase()}`}>
                      {riskAssessment.riskLevel}
                    </span>
                    <span className="branch-subtag">{riskAssessment.branchName || selectedBranch}</span>
                  </div>

                  <div className="risk-metrics-grid">
                    <div className="metric-item">
                      <span className="metric-label">Analyzed Findings</span>
                      <span className="metric-val">{riskAssessment.totalFindings}</span>
                    </div>
                    <div className="metric-item">
                      <span className="metric-label">Critical / High Severity</span>
                      <span className="metric-val text-danger">
                        {riskAssessment.criticalFindings} / {riskAssessment.highFindings}
                      </span>
                    </div>
                    <div className="metric-item">
                      <span className="metric-label">Files Evaluated in Branch</span>
                      <span className="metric-val">{riskAssessment.totalFilesChanged}</span>
                    </div>
                    <div className="metric-item">
                      <span className="metric-label">Branch Line Churn</span>
                      <span className="metric-val">
                        +{riskAssessment.totalAdditions} / -{riskAssessment.totalDeletions}
                      </span>
                    </div>
                  </div>
                </section>
              )}

              {/* AI Explanation based on Evidence */}
              {riskAssessment && riskAssessment.aiExplanation && (
                <section className="explanation-section">
                  <h3>Developer Risk Advisory & Impact Summary ({selectedBranch})</h3>
                  <div className="explanation-content">
                    <pre>{riskAssessment.aiExplanation}</pre>
                  </div>
                </section>
              )}

              {/* Navigation Tabs */}
              <div className="content-tabs">
                <button
                  className={`tab-btn ${activeTab === "priorities" ? "active" : ""}`}
                  onClick={() => setActiveTab("priorities")}
                >
                  Prioritized Action Items ({prioritizedList.length})
                </button>
                <button
                  className={`tab-btn ${activeTab === "commits" ? "active" : ""}`}
                  onClick={() => setActiveTab("commits")}
                >
                  Branch Commit History ({commits.length})
                </button>
                <button
                  className={`tab-btn ${activeTab === "allFindings" ? "active" : ""}`}
                  onClick={() => setActiveTab("allFindings")}
                >
                  Raw Findings ({findings.length})
                </button>
              </div>

              {/* TAB 1: PRIORITIZED ACTION ITEMS */}
              {activeTab === "priorities" && (
                <section className="prioritized-section">
                  <div className="section-intro">
                    <h3>Developer Action Plan: Prioritized by Risk Criticality (Branch: {selectedBranch})</h3>
                    <p>
                      Review errors introduced in development stages on branch <strong>{selectedBranch}</strong>.
                      Understand why they happened, and fix high-criticality vulnerabilities first to ensure a clean merge.
                    </p>
                  </div>

                  {prioritizedList.length === 0 ? (
                    <div className="empty-state-card">
                      <p>No critical risks detected in branch '{selectedBranch}'. Code checks passed!</p>
                    </div>
                  ) : (
                    <div className="prioritized-list">
                      {prioritizedList.map((item) => (
                        <div key={item.id} className={`prioritized-card severity-${item.severity.toLowerCase()}`}>
                          <div className="prioritized-card-header">
                            <div className="header-badges">
                              <span className={getPriorityBadgeClass(item.priorityRank)}>
                                Rank #{item.priorityRank} Priority
                              </span>
                              <span className={getSeverityBadgeClass(item.severity)}>
                                {item.severity}
                              </span>
                              <span className="rule-badge">{item.ruleId}</span>
                            </div>
                            <div className="location-badge">
                              {item.filePath ? (
                                <code>{item.filePath}:{item.lineNumber > 0 ? item.lineNumber : 1}</code>
                              ) : (
                                <code>Global Change</code>
                              )}
                            </div>
                          </div>

                          <h4 className="issue-message">{item.message}</h4>

                          {/* Criticality Impact */}
                          {item.criticalityReason && (
                            <div className="reason-banner">
                              <strong>Why this is Critical:</strong> {item.criticalityReason}
                            </div>
                          )}

                          {/* Dual Guidance Grid: Why it happened & How to fix */}
                          <div className="guidance-grid">
                            <div className="guidance-box root-cause-box">
                              <div className="guidance-box-header">
                                <span className="icon">⚠️</span>
                                <strong>Why the Error Happened (Root Cause)</strong>
                              </div>
                              <p>{item.rootCause || "Code changes introduced an anti-pattern or security violation in the committed diff."}</p>
                            </div>

                            <div className="guidance-box remediation-box">
                              <div className="guidance-box-header">
                                <span className="icon">🛠️</span>
                                <strong>How to Fix This First (Remediation)</strong>
                              </div>
                              <p>{item.remediation || "Refactor the flagged lines according to secure development standards."}</p>
                            </div>
                          </div>

                          {/* Commit Context Footer */}
                          {(item.commitSha || item.commitMessage) && (
                            <div className="commit-source-footer">
                              <span className="footer-label">Detected in Branch '{selectedBranch}' Commit:</span>
                              {item.commitSha && (
                                <a
                                  href={`${selectedRepo.repositoryUrl}/commit/${item.commitSha}`}
                                  target="_blank"
                                  rel="noreferrer"
                                  className="commit-sha-link"
                                >
                                  {item.commitSha.substring(0, 7)}
                                </a>
                              )}
                              {item.commitMessage && (
                                <span className="commit-msg-text">"{item.commitMessage}"</span>
                              )}
                            </div>
                          )}
                        </div>
                      ))}
                    </div>
                  )}
                </section>
              )}

              {/* TAB 2: BRANCH COMMIT HISTORY */}
              {activeTab === "commits" && (
                <section className="commits-history-section">
                  <div className="section-intro">
                    <h3>Commit History on Branch: {selectedBranch}</h3>
                    <p>Commits ingested for branch '{selectedBranch}'. Each commit's unified diff is inspected for code vulnerabilities.</p>
                  </div>

                  {commits.length === 0 ? (
                    <div className="empty-state-card">
                      <p>No commits ingested yet for branch '{selectedBranch}'. Click "Switch & Analyze Branch" above.</p>
                    </div>
                  ) : (
                    <div className="commits-table-wrap">
                      <table className="commits-table">
                        <thead>
                          <tr>
                            <th>Commit SHA</th>
                            <th>Message</th>
                            <th>Author</th>
                            <th>Committed Date</th>
                            <th>Action</th>
                          </tr>
                        </thead>
                        <tbody>
                          {commits.map((c) => (
                            <tr key={c.id}>
                              <td>
                                <a
                                  href={`${selectedRepo.repositoryUrl}/commit/${c.sha}`}
                                  target="_blank"
                                  rel="noreferrer"
                                  className="commit-sha-link"
                                >
                                  <code>{c.sha.substring(0, 7)}</code>
                                </a>
                              </td>
                              <td className="commit-message-cell">{c.message}</td>
                              <td>{c.authorName || "Unknown"}</td>
                              <td className="date-cell">
                                {c.committedAt ? new Date(c.committedAt).toLocaleString() : "-"}
                              </td>
                              <td>
                                <a
                                  href={`${selectedRepo.repositoryUrl}/commit/${c.sha}`}
                                  target="_blank"
                                  rel="noreferrer"
                                  className="view-btn"
                                >
                                  View on GitHub ↗
                                </a>
                              </td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  )}
                </section>
              )}

              {/* TAB 3: ALL RAW FINDINGS */}
              {activeTab === "allFindings" && (
                <section className="findings-section">
                  <div className="section-intro">
                    <h3>Static Evidence & Findings on '{selectedBranch}' ({findings.length})</h3>
                    <p>Raw findings detected across changesets and analyzed commits on this branch.</p>
                  </div>
                  {findings.length === 0 ? (
                    <div className="empty-state-card">
                      <p>No active static security or quality findings in branch '{selectedBranch}'.</p>
                    </div>
                  ) : (
                    <div className="findings-table-wrap">
                      <table className="findings-table">
                        <thead>
                          <tr>
                            <th>Severity</th>
                            <th>Rule ID</th>
                            <th>Issue Message</th>
                            <th>Line</th>
                            <th>Analyzer</th>
                          </tr>
                        </thead>
                        <tbody>
                          {findings.map((f) => (
                            <tr key={f.id}>
                              <td>
                                <span className={getSeverityBadgeClass(f.severity)}>{f.severity}</span>
                              </td>
                              <td><code>{f.ruleId}</code></td>
                              <td>{f.message}</td>
                              <td>Line {f.lineNumber}</td>
                              <td>{f.analyzer}</td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  )}
                </section>
              )}
            </>
          ) : (
            <div className="placeholder-view">
              <h2>Welcome to CodeSure / DevSentinel</h2>
              <p>Connect a GitHub repository to switch between branches, analyze developing changes, and prioritize remediation for developers.</p>
            </div>
          )}
        </main>
      </div>
    </div>
  );
}
