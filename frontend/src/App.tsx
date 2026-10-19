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

interface RiskAssessment {
  repositoryId: string;
  repositoryName: string;
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
}

export default function App() {
  const [backendStatus, setBackendStatus] = useState("Checking...");
  const [repositories, setRepositories] = useState<Repository[]>([]);
  const [selectedRepo, setSelectedRepo] = useState<Repository | null>(null);
  const [repoUrlInput, setRepoUrlInput] = useState("");
  const [loading, setLoading] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  const [commits, setCommits] = useState<Commit[]>([]);
  const [findings, setFindings] = useState<Finding[]>([]);
  const [riskAssessment, setRiskAssessment] = useState<RiskAssessment | null>(null);

  // Health Check
  useEffect(() => {
    fetch("http://localhost:8080/api/health")
      .then((res) => (res.ok ? res.json() : Promise.reject("Down")))
      .then((data) => {
        if (data.status === "UP") setBackendStatus("Connected");
        else setBackendStatus("Unavailable");
      })
      .catch(() => setBackendStatus("Disconnected"));
  }, []);

  // Fetch Repositories
  const loadRepositories = () => {
    fetch("http://localhost:8080/api/repositories")
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

  const selectRepository = (repo: Repository) => {
    setSelectedRepo(repo);
    setErrorMsg(null);

    // Fetch findings
    fetch(`http://localhost:8080/api/repositories/${repo.id}/findings`)
      .then((res) => (res.ok ? res.json() : []))
      .then((data) => setFindings(data))
      .catch(() => setFindings([]));

    // Fetch risk assessment
    fetch(`http://localhost:8080/api/repositories/${repo.id}/risk-assessment`)
      .then((res) => (res.ok ? res.json() : null))
      .then((data) => setRiskAssessment(data))
      .catch(() => setRiskAssessment(null));
  };

  const handleRegisterRepo = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!repoUrlInput.trim()) return;

    setLoading(true);
    setErrorMsg(null);
    try {
      const res = await fetch("http://localhost:8080/api/repositories/sync-url", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ repositoryUrl: repoUrlInput.trim() }),
      });

      if (!res.ok) {
        const err = await res.text();
        throw new Error(err || "Failed to register repository");
      }

      const created: Repository = await res.json();
      setRepoUrlInput("");
      loadRepositories();
      selectRepository(created);

      // Auto sync initial commits
      await fetch(`http://localhost:8080/api/repositories/${created.id}/sync-commits?limit=15`, {
        method: "POST",
      });
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

  const handleSyncCommits = async () => {
    if (!selectedRepo) return;
    setLoading(true);
    try {
      const res = await fetch(`http://localhost:8080/api/repositories/${selectedRepo.id}/sync-commits?limit=15`, {
        method: "POST",
      });
      if (res.ok) {
        const data: Commit[] = await res.json();
        setCommits(data);
        // Refresh risk assessment
        selectRepository(selectedRepo);
      }
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
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
          <span className="pill online">Database: PostgreSQL Connected</span>
          <span className="pill badge-pill">Principle: Evidence Before AI</span>
        </div>
      </header>

      <div className="codesure-layout">
        {/* Sidebar */}
        <aside className="codesure-sidebar">
          <h3>Registered Repositories</h3>
          <form onSubmit={handleRegisterRepo} className="repo-form">
            <input
              type="text"
              placeholder="https://github.com/owner/repo"
              value={repoUrlInput}
              onChange={(e) => setRepoUrlInput(e.target.value)}
              disabled={loading}
            />
            <button type="submit" disabled={loading || !repoUrlInput.trim()}>
              {loading ? "Syncing..." : "+ Connect Repo"}
            </button>
          </form>

          {errorMsg && <div className="error-alert">{errorMsg}</div>}

          <div className="repo-list">
            {repositories.length === 0 ? (
              <p className="empty-hint">No repositories connected yet. Add one above.</p>
            ) : (
              repositories.map((repo) => (
                <div
                  key={repo.id}
                  className={`repo-card ${selectedRepo?.id === repo.id ? "active" : ""}`}
                  onClick={() => selectRepository(repo)}
                >
                  <h4>{repo.name}</h4>
                  <p className="repo-url">{repo.repositoryUrl}</p>
                  <span className="tag">Branch: {repo.defaultBranch}</span>
                </div>
              ))
            )}
          </div>
        </aside>

        {/* Main Content */}
        <main className="codesure-main">
          {selectedRepo ? (
            <>
              <div className="repo-header-actions">
                <div>
                  <h2>{selectedRepo.name}</h2>
                  <a href={selectedRepo.repositoryUrl} target="_blank" rel="noreferrer" className="link">
                    {selectedRepo.repositoryUrl}
                  </a>
                </div>
                <div className="action-buttons">
                  <span className="pill online">Synced Commits: {commits.length}</span>
                  <button onClick={handleSyncCommits} disabled={loading} className="btn-secondary">
                    {loading ? "Syncing..." : "Sync Latest Commits"}
                  </button>
                </div>
              </div>

              {/* Risk Intelligence Card */}
              {riskAssessment && (
                <section className="risk-overview-card">
                  <div className="risk-score-box">
                    <span className="risk-title">OVERALL CHANGE RISK</span>
                    <div className="score-value">{riskAssessment.overallRiskScore}</div>
                    <span className={`risk-level-tag ${riskAssessment.riskLevel.toLowerCase()}`}>
                      {riskAssessment.riskLevel}
                    </span>
                  </div>

                  <div className="risk-metrics-grid">
                    <div className="metric-item">
                      <span className="metric-label">Findings Count</span>
                      <span className="metric-val">{riskAssessment.totalFindings}</span>
                    </div>
                    <div className="metric-item">
                      <span className="metric-label">Critical / High</span>
                      <span className="metric-val text-danger">
                        {riskAssessment.criticalFindings} / {riskAssessment.highFindings}
                      </span>
                    </div>
                    <div className="metric-item">
                      <span className="metric-label">Files Evaluated</span>
                      <span className="metric-val">{riskAssessment.totalFilesChanged}</span>
                    </div>
                    <div className="metric-item">
                      <span className="metric-label">Code Churn</span>
                      <span className="metric-val">
                        +{riskAssessment.totalAdditions} / -{riskAssessment.totalDeletions}
                      </span>
                    </div>
                  </div>
                </section>
              )}

              {/* AI Explanation based on Evidence */}
              {riskAssessment && (
                <section className="explanation-section">
                  <h3>Evidence-Based Risk Reasoning</h3>
                  <div className="explanation-content">
                    <pre>{riskAssessment.aiExplanation}</pre>
                  </div>
                </section>
              )}

              {/* Static Analysis Findings */}
              <section className="findings-section">
                <h3>Detected Evidence & Findings ({findings.length})</h3>
                {findings.length === 0 ? (
                  <div className="no-findings">
                    <p>No active static security or quality findings in this change set.</p>
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
            </>
          ) : (
            <div className="placeholder-view">
              <h2>Welcome to CodeSure / DevSentinel</h2>
              <p>Connect a GitHub repository to monitor code changes, extract static evidence, and predict release risk.</p>
            </div>
          )}
        </main>
      </div>
    </div>
  );
}