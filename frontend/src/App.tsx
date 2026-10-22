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

interface ChurnWindow {
  windowName: string;
  commitCount: number;
  additions: number;
  deletions: number;
  totalChurn: number;
  activeAuthors: number;
}

interface FileHotspot {
  filePath: string;
  changeCount: number;
  additions: number;
  deletions: number;
  totalChurn: number;
  findingsCount: number;
  criticalFindings: number;
  highFindings: number;
  mediumFindings: number;
  lowFindings: number;
  hotspotScore: number;
  riskLevel: "CRITICAL" | "HIGH" | "MEDIUM" | "LOW";
  recommendation: string;
}

interface DailyActivity {
  date: string;
  commitCount: number;
  additions: number;
  deletions: number;
  totalChurn: number;
}

interface AuthorMetric {
  authorName: string;
  commitCount: number;
  additions: number;
  deletions: number;
  totalChurn: number;
  churnPercentage: number;
}

interface HistoricalMetrics {
  repositoryId: string;
  repositoryName: string;
  branch: string;
  churn7Days: ChurnWindow;
  churn30Days: ChurnWindow;
  churnAllTime: ChurnWindow;
  repositoryStabilityIndex: number;
  stabilityAssessment: string;
  topHotspots: FileHotspot[];
  dailyTimeline: DailyActivity[];
  topAuthors: AuthorMetric[];
}

interface FixSuggestion {
  findingId: string;
  ruleId: string;
  filePath: string;
  lineNumber: number;
  title: string;
  beforeSnippet: string;
  afterSnippet: string;
  unifiedDiff: string;
  explanation: string;
  confidenceLevel: string;
  securityBestPractices: string[];
}

interface SiloedFile {
  filePath: string;
  dominantAuthor: string;
  dominancePercentage: number;
  totalCommits: number;
  totalChurn: number;
  defectCount: number;
  criticalDefects: number;
  siloRiskLevel: string;
  recommendation: string;
}

interface ContributorProfile {
  authorName: string;
  commitCount: number;
  additions: number;
  deletions: number;
  totalChurn: number;
  churnPercentage: number;
  siloedFilesCount: number;
  defectsAssociated: number;
  criticalDefectsAssociated: number;
  contributorRiskLevel: string;
  recommendation: string;
}

interface BusFactorAnalysis {
  repositoryId: string;
  repositoryName: string;
  branch: string;
  overallBusFactor: number;
  busFactorStatus: string;
  coreAuthorsConcentration: number;
  totalSiloedFiles: number;
  executiveSummary: string;
  highRiskSilos: SiloedFile[];
  contributorProfiles: ContributorProfile[];
  teamRecommendations: string[];
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

  // GitHub Token State (Persisted in localStorage for 5,000 reqs/hr)
  const [githubToken, setGithubToken] = useState<string>(() => localStorage.getItem("codesure_github_token") || "");
  const [showTokenModal, setShowTokenModal] = useState(false);
  const [tokenInput, setTokenInput] = useState("");

  // Branch switching state
  const [branches, setBranches] = useState<string[]>([]);
  const [selectedBranch, setSelectedBranch] = useState<string>("main");
  const [loadingBranches, setLoadingBranches] = useState<boolean>(false);
  const [customBranchInput, setCustomBranchInput] = useState<string>("");
  const [showCustomBranch, setShowCustomBranch] = useState<boolean>(false);

  const [commits, setCommits] = useState<Commit[]>([]);
  const [findings, setFindings] = useState<Finding[]>([]);
  const [riskAssessment, setRiskAssessment] = useState<RiskAssessment | null>(null);
  const [historicalMetrics, setHistoricalMetrics] = useState<HistoricalMetrics | null>(null);
  const [loadingMetrics, setLoadingMetrics] = useState<boolean>(false);
  const [busFactorData, setBusFactorData] = useState<BusFactorAnalysis | null>(null);
  const [activeFixModal, setActiveFixModal] = useState<FixSuggestion | null>(null);
  const [loadingFixId, setLoadingFixId] = useState<string | null>(null);
  const [copiedDiff, setCopiedDiff] = useState<boolean>(false);
  const [activeTab, setActiveTab] = useState<"priorities" | "historical" | "commits" | "allFindings">("priorities");

  // Build headers with optional X-GitHub-Token
  const getHeaders = (extra: Record<string, string> = {}) => {
    const h: Record<string, string> = { ...extra };
    if (githubToken.trim()) {
      h["X-GitHub-Token"] = githubToken.trim();
    }
    return h;
  };

  // Sync token to backend when updated
  useEffect(() => {
    if (githubToken) {
      fetch(`${API_BASE}/api/repositories/github-token`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ token: githubToken }),
      }).catch(() => {});
    }
  }, [githubToken]);

  // Health Check with auto-retry for Render cold starts
  useEffect(() => {
    let timer: ReturnType<typeof setTimeout>;

    const checkHealth = () => {
      fetch(`${API_BASE}/api/health`, { headers: getHeaders() })
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
    fetch(`${API_BASE}/api/repositories`, { headers: getHeaders() })
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
    fetch(`${API_BASE}/api/repositories/${repoId}/commits?branch=${encodedBranch}`, { headers: getHeaders() })
      .then((res) => (res.ok ? res.json() : []))
      .then((data) => setCommits(data))
      .catch(() => setCommits([]));

    // Fetch branch findings
    fetch(`${API_BASE}/api/repositories/${repoId}/findings?branch=${encodedBranch}`, { headers: getHeaders() })
      .then((res) => (res.ok ? res.json() : []))
      .then((data) => setFindings(data))
      .catch(() => setFindings([]));

    // Fetch branch risk assessment
    fetch(`${API_BASE}/api/repositories/${repoId}/risk-assessment?branch=${encodedBranch}`, { headers: getHeaders() })
      .then((res) => (res.ok ? res.json() : null))
      .then((data) => setRiskAssessment(data))
      .catch(() => setRiskAssessment(null));

    // Fetch branch historical & churn metrics
    setLoadingMetrics(true);
    fetch(`${API_BASE}/api/repositories/${repoId}/historical-metrics?branch=${encodedBranch}`, { headers: getHeaders() })
      .then((res) => (res.ok ? res.json() : null))
      .then((data) => setHistoricalMetrics(data))
      .catch(() => setHistoricalMetrics(null))
      .finally(() => setLoadingMetrics(false));

    // Fetch branch bus factor & contributor risk analysis
    fetch(`${API_BASE}/api/repositories/${repoId}/bus-factor?branch=${encodedBranch}`, { headers: getHeaders() })
      .then((res) => (res.ok ? res.json() : null))
      .then((data) => setBusFactorData(data))
      .catch(() => setBusFactorData(null));
  };

  const fetchBranches = async (repo: Repository) => {
    setLoadingBranches(true);
    try {
      const res = await fetch(`${API_BASE}/api/repositories/${repo.id}/branches`, { headers: getHeaders() });
      if (res.ok) {
        const branchList: string[] = await res.json();
        if (branchList && branchList.length > 0) {
          setBranches(branchList);
          return branchList;
        }
      } else {
        const err = await res.json().catch(() => ({}));
        if (err.message && err.message.includes("Rate Limit")) {
          setErrorMsg(err.message);
        }
      }
    } catch {
      // Fallback
    } finally {
      setLoadingBranches(false);
    }
    const def = [repo.defaultBranch || "main"];
    setBranches(def);
    return def;
  };

  const selectRepository = async (repo: Repository, branchToSelect?: string) => {
    setSelectedRepo(repo);
    setErrorMsg(null);
    setShowCustomBranch(false);

    const initialBranch = branchToSelect || repo.defaultBranch || "main";
    setSelectedBranch(initialBranch);

    const branchList = await fetchBranches(repo);
    let effectiveBranch = initialBranch;
    if (branchList.length > 0 && !branchList.includes(initialBranch)) {
      effectiveBranch = branchList[0];
      setSelectedBranch(effectiveBranch);
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
        headers: getHeaders({ "Content-Type": "application/json" }),
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

  const handleDeleteRepo = async (repoId: string, repoName: string) => {
    if (!window.confirm(`Are you sure you want to delete repository "${repoName}" and all its analyzed data?`)) {
      return;
    }

    try {
      const res = await fetch(`${API_BASE}/api/repositories/${repoId}`, {
        method: "DELETE",
        headers: getHeaders(),
      });

      if (!res.ok) {
        let detail = `HTTP ${res.status}`;
        try {
          const errJson = await res.json();
          detail = errJson.message || errJson.error || detail;
        } catch {
          const text = await res.text();
          if (text) detail = text;
        }
        if (res.status === 404) {
          throw new Error(
            `Failed to delete repository (HTTP 404). If your backend is deployed on Render, update the Render branch to 'feature/deep-change-analysis'.`
          );
        }
        throw new Error(`Failed to delete repository: ${detail}`);
      }

      const updated = repositories.filter((r) => r.id !== repoId);
      setRepositories(updated);

      if (selectedRepo?.id === repoId) {
        if (updated.length > 0) {
          selectRepository(updated[0]);
        } else {
          setSelectedRepo(null);
          setCommits([]);
          setFindings([]);
          setRiskAssessment(null);
          setHistoricalMetrics(null);
          setBusFactorData(null);
          setActiveFixModal(null);
          setBranches([]);
        }
      }
    } catch (err: unknown) {
      if (err instanceof Error) {
        setErrorMsg(err.message);
      }
    }
  };

  const handleSwitchAndAnalyzeBranch = async (targetBranch: string) => {
    if (!selectedRepo) return;
    const cleanBranch = targetBranch.trim();
    if (!cleanBranch) return;

    setSelectedBranch(cleanBranch);
    setErrorMsg(null);
    if (!branches.includes(cleanBranch)) {
      setBranches((prev) => [...prev, cleanBranch]);
    }

    setLoadingCommits(true);
    try {
      const res = await fetch(
        `${API_BASE}/api/repositories/${selectedRepo.id}/sync-commits?branch=${encodeURIComponent(cleanBranch)}&limit=30`,
        { method: "POST", headers: getHeaders() }
      );

      if (!res.ok) {
        let errText = `Failed to sync commits for branch "${cleanBranch}"`;
        try {
          const errJson = await res.json();
          errText = errJson.message || errJson.error || errText;
        } catch {
          errText = await res.text();
        }
        throw new Error(errText);
      }

      const data: Commit[] = await res.json();
      setCommits(data);
      loadBranchData(selectedRepo.id, cleanBranch);
    } catch (err: unknown) {
      if (err instanceof Error) {
        setErrorMsg(err.message);
      }
    } finally {
      setLoadingCommits(false);
    }
  };

  const handleCustomBranchSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!customBranchInput.trim()) return;
    const branch = customBranchInput.trim();
    handleSwitchAndAnalyzeBranch(branch);
    setCustomBranchInput("");
    setShowCustomBranch(false);
  };

  const handleSaveToken = () => {
    const trimmed = tokenInput.trim();
    setGithubToken(trimmed);
    localStorage.setItem("codesure_github_token", trimmed);
    setShowTokenModal(false);
    setErrorMsg(null);
    if (selectedRepo) {
      fetchBranches(selectedRepo);
    }
  };

  const handleClearToken = () => {
    setGithubToken("");
    localStorage.removeItem("codesure_github_token");
    setTokenInput("");
    setShowTokenModal(false);
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

  const getStabilityClass = (score: number) => {
    if (score >= 80) return "stable";
    if (score >= 60) return "moderate";
    if (score >= 40) return "elevated";
    return "critical";
  };

  const getBusFactorStatusClass = (status: string) => {
    switch (status) {
      case "CRITICAL_RISK":
        return "badge critical";
      case "MODERATE_RISK":
        return "badge high";
      default:
        return "badge low";
    }
  };

  const getSiloBadgeClass = (level: string) => {
    if (level.includes("CRITICAL")) return "badge critical";
    if (level.includes("HIGH")) return "badge high";
    return "badge medium";
  };

  const handleOpenFixModal = async (findingId: string) => {
    setLoadingFixId(findingId);
    setCopiedDiff(false);
    try {
      const res = await fetch(`${API_BASE}/api/analyzer/fix-suggestion/${findingId}`, { headers: getHeaders() });
      if (res.ok) {
        const data: FixSuggestion = await res.json();
        setActiveFixModal(data);
      }
    } catch (e) {
      console.error("Failed to load fix suggestion", e);
    } finally {
      setLoadingFixId(null);
    }
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
          <button
            type="button"
            className={`pill token-pill ${githubToken ? "online" : "warning-pill"}`}
            onClick={() => {
              setTokenInput(githubToken);
              setShowTokenModal(true);
            }}
          >
            🔑 GitHub Token: {githubToken ? "Active (5,000/hr)" : "Unset (60/hr Limit)"}
          </button>
          <span className="pill badge-pill">Multi-Branch Vulnerability Analysis</span>
        </div>
      </header>

      {/* GitHub Token Modal */}
      {showTokenModal && (
        <div className="modal-backdrop" onClick={() => setShowTokenModal(false)}>
          <div className="modal-content" onClick={(e) => e.stopPropagation()}>
            <h3>🔑 GitHub Personal Access Token</h3>
            <p>
              GitHub strictly caps unauthenticated API calls to <strong>60 requests per hour</strong>.
              Adding a free Personal Access Token unlocks <strong>5,000 requests per hour</strong>, ensuring
              branch discovery, commit history, and deep diff inspection work smoothly.
            </p>
            <div className="token-instructions">
              <span>Generate a token at: </span>
              <a
                href="https://github.com/settings/tokens/new?description=CodeSure&scopes=public_repo"
                target="_blank"
                rel="noreferrer"
                className="link"
              >
                github.com/settings/tokens (no extra scopes needed for public repos) ↗
              </a>
            </div>
            <input
              type="password"
              placeholder="ghp_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
              value={tokenInput}
              onChange={(e) => setTokenInput(e.target.value)}
              className="token-input"
              autoFocus
            />
            <div className="modal-actions">
              <button onClick={handleSaveToken} className="btn-primary">
                Save Token
              </button>
              {githubToken && (
                <button onClick={handleClearToken} className="btn-secondary">
                  Remove Token
                </button>
              )}
              <button onClick={() => setShowTokenModal(false)} className="btn-outline-small">
                Cancel
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Automated Remediation Fix & Diff Modal */}
      {activeFixModal && (
        <div className="modal-backdrop" onClick={() => setActiveFixModal(null)}>
          <div className="modal-content fix-modal-content" onClick={(e) => e.stopPropagation()}>
            <div className="fix-modal-header">
              <div className="header-meta">
                <span className="pill badge-pill">✨ One-Click Auto-Fix</span>
                <span className="confidence-pill">{activeFixModal.confidenceLevel} CONFIDENCE</span>
                <span className="rule-badge">{activeFixModal.ruleId}</span>
              </div>
              <button className="btn-close-modal" onClick={() => setActiveFixModal(null)}>✕</button>
            </div>

            <h3 className="fix-modal-title">{activeFixModal.title}</h3>
            <p className="fix-location">
              File Target: <code>{activeFixModal.filePath}:{activeFixModal.lineNumber}</code>
            </p>

            <div className="fix-explanation-box">
              <strong>Security Vulnerability Analysis:</strong>
              <p>{activeFixModal.explanation}</p>
            </div>

            {/* Unified Diff View */}
            <div className="diff-container">
              <div className="diff-header">
                <span>Unified Git Patch (.diff)</span>
                <button
                  type="button"
                  className="btn-copy-patch"
                  onClick={() => {
                    navigator.clipboard.writeText(activeFixModal.unifiedDiff);
                    setCopiedDiff(true);
                    setTimeout(() => setCopiedDiff(false), 2500);
                  }}
                >
                  {copiedDiff ? "✓ Patch Copied!" : "📋 Copy Diff Patch"}
                </button>
              </div>
              <pre className="diff-pre">
                {activeFixModal.unifiedDiff.split("\n").map((line, idx) => {
                  let lineClass = "diff-line";
                  if (line.startsWith("+") && !line.startsWith("+++")) lineClass += " diff-add";
                  else if (line.startsWith("-") && !line.startsWith("---")) lineClass += " diff-del";
                  else if (line.startsWith("@@")) lineClass += " diff-hunk";
                  return (
                    <div key={idx} className={lineClass}>
                      {line}
                    </div>
                  );
                })}
              </pre>
            </div>

            {/* Side-by-side Before & After */}
            <div className="code-comparison-grid">
              <div className="code-box before-box">
                <div className="code-box-header">
                  <span className="dot dot-red"></span>
                  <span>Vulnerable Code (Before)</span>
                </div>
                <pre className="code-content">{activeFixModal.beforeSnippet}</pre>
              </div>

              <div className="code-box after-box">
                <div className="code-box-header">
                  <span className="dot dot-green"></span>
                  <span>Remediated Safe Code (After)</span>
                  <button
                    type="button"
                    className="btn-copy-code"
                    onClick={() => {
                      navigator.clipboard.writeText(activeFixModal.afterSnippet);
                      setCopiedDiff(true);
                      setTimeout(() => setCopiedDiff(false), 2500);
                    }}
                  >
                    Copy Code
                  </button>
                </div>
                <pre className="code-content">{activeFixModal.afterSnippet}</pre>
              </div>
            </div>

            {/* Best practices checklist */}
            {activeFixModal.securityBestPractices && activeFixModal.securityBestPractices.length > 0 && (
              <div className="best-practices-box">
                <h4>🛡️ Prescriptive Security Checklist</h4>
                <ul>
                  {activeFixModal.securityBestPractices.map((bp, i) => (
                    <li key={i}>{bp}</li>
                  ))}
                </ul>
              </div>
            )}

            <div className="modal-actions">
              <button
                type="button"
                className="btn-primary"
                onClick={() => {
                  navigator.clipboard.writeText(activeFixModal.afterSnippet);
                  setCopiedDiff(true);
                  setTimeout(() => setCopiedDiff(false), 2500);
                }}
              >
                {copiedDiff ? "✓ Code Copied to Clipboard!" : "Copy Clean Code"}
              </button>
              <button type="button" className="btn-secondary" onClick={() => setActiveFixModal(null)}>
                Close
              </button>
            </div>
          </div>
        </div>
      )}

      <div className="codesure-layout">
        {/* Sidebar */}
        <aside className="codesure-sidebar">
          <h3>Connected Repositories ({repositories.length})</h3>
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

          {errorMsg && (
            <div className="error-alert">
              <span>{errorMsg}</span>
              {!githubToken && (
                <button
                  type="button"
                  className="btn-token-quick"
                  onClick={() => setShowTokenModal(true)}
                >
                  Add GitHub Token (Fix 403 Rate Limit)
                </button>
              )}
            </div>
          )}

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
                  <div className="repo-card-top">
                    <h4>{repo.name}</h4>
                    <button
                      className="btn-delete-repo"
                      title="Delete Repository"
                      onClick={(e) => {
                        e.stopPropagation();
                        handleDeleteRepo(repo.id, repo.name);
                      }}
                    >
                      ✕
                    </button>
                  </div>
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
                    <button
                      onClick={() => handleDeleteRepo(selectedRepo.id, selectedRepo.name)}
                      className="btn-danger-outline"
                      title="Delete this repository"
                    >
                      🗑 Delete
                    </button>
                  </div>
                </div>

                {/* Branch Switcher Controls */}
                <div className="branch-switcher-bar">
                  <div className="branch-selector-group">
                    <span className="branch-icon">🌿</span>
                    <label htmlFor="branch-select" className="branch-label">Branch:</label>
                    <select
                      id="branch-select"
                      className="branch-select"
                      value={selectedBranch}
                      disabled={loadingBranches || loadingCommits}
                      onChange={(e) => {
                        if (e.target.value === "__custom__") {
                          setShowCustomBranch(true);
                        } else {
                          handleSwitchAndAnalyzeBranch(e.target.value);
                        }
                      }}
                    >
                      {branches.map((b) => (
                        <option key={b} value={b}>
                          {b} {b === selectedRepo.defaultBranch ? "(default)" : ""}
                        </option>
                      ))}
                      <option value="__custom__">+ Enter Custom Branch...</option>
                    </select>

                    <button
                      type="button"
                      className="btn-switch-branch"
                      disabled={loadingCommits}
                      onClick={() => handleSwitchAndAnalyzeBranch(selectedBranch)}
                    >
                      {loadingCommits ? "Analyzing..." : "Analyze Active Branch"}
                    </button>

                    <button
                      type="button"
                      className="btn-outline-small"
                      onClick={() => setShowCustomBranch(!showCustomBranch)}
                    >
                      {showCustomBranch ? "Cancel" : "+ Custom Branch"}
                    </button>

                    <button
                      type="button"
                      className="btn-icon-refresh"
                      title="Refresh remote branches from GitHub"
                      disabled={loadingBranches}
                      onClick={() => selectedRepo && fetchBranches(selectedRepo)}
                    >
                      {loadingBranches ? "..." : "↻"}
                    </button>
                  </div>

                  {showCustomBranch && (
                    <form onSubmit={handleCustomBranchSubmit} className="custom-branch-form">
                      <input
                        type="text"
                        placeholder="e.g. develop, feature/auth, bugfix/login"
                        value={customBranchInput}
                        onChange={(e) => setCustomBranchInput(e.target.value)}
                        className="custom-branch-input"
                        autoFocus
                      />
                      <button type="submit" disabled={!customBranchInput.trim() || loadingCommits} className="btn-switch-branch">
                        Switch & Analyze
                      </button>
                    </form>
                  )}

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
                  className={`tab-btn ${activeTab === "historical" ? "active" : ""}`}
                  onClick={() => setActiveTab("historical")}
                >
                  Historical Churn & Hotspots {historicalMetrics ? `(${historicalMetrics.topHotspots.length})` : ""}
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

                          {/* One-Click Auto-Fix Button */}
                          <div className="card-action-bar">
                            <button
                              type="button"
                              className="btn-auto-fix"
                              disabled={loadingFixId === item.id}
                              onClick={() => handleOpenFixModal(item.id)}
                            >
                              {loadingFixId === item.id ? "✨ Generating Fix Patch..." : "✨ View Remediation Diff & Auto-Fix"}
                            </button>
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

              {/* TAB: HISTORICAL CHURN & FILE HOTSPOTS */}
              {activeTab === "historical" && (
                <section className="historical-section">
                  <div className="section-intro">
                    <h3>Historical Churn & File Hotspot Intelligence ({selectedBranch})</h3>
                    <p>
                      Time-windowed code churn analytics, defect density correlation, and predictive hotspot analysis across commits on branch <strong>{selectedBranch}</strong>.
                    </p>
                  </div>

                  {loadingMetrics ? (
                    <div className="empty-state-card">
                      <p>Calculating historical metrics, time-window churn, and file hotspots...</p>
                    </div>
                  ) : !historicalMetrics ? (
                    <div className="empty-state-card">
                      <p>No historical metrics available yet. Ingest commits or switch branches to generate churn analytics.</p>
                    </div>
                  ) : (
                    <>
                      {/* Stability Index & Health Banner */}
                      <div className="stability-banner">
                        <div className="stability-score-box">
                          <span className="stability-label">Stability Health Index</span>
                          <div className="stability-value">
                            {historicalMetrics.repositoryStabilityIndex}
                            <span className="stability-max">/100</span>
                          </div>
                          <span className={`stability-tag ${getStabilityClass(historicalMetrics.repositoryStabilityIndex)}`}>
                            {historicalMetrics.stabilityAssessment}
                          </span>
                        </div>
                        <div className="stability-explanation">
                          <h4>Risk Volatility & Health Assessment</h4>
                          <p>
                            Evaluates code volatility by correlating 7-day vs 30-day line churn velocity against static defect density.
                            Higher scores indicate controlled code changes and minimal critical findings.
                          </p>
                          <div className="stability-meta">
                            <span>Branch: <strong>{historicalMetrics.branch}</strong></span>
                            <span>Top Hotspots Identified: <strong>{historicalMetrics.topHotspots.length}</strong></span>
                            <span>Active Contributors: <strong>{historicalMetrics.topAuthors.length}</strong></span>
                          </div>
                        </div>
                      </div>

                      {/* Time-Windowed Churn Summary Cards */}
                      <h4 className="sub-heading">Time-Windowed Code Churn</h4>
                      <div className="churn-cards-grid">
                        <div className="churn-card">
                          <div className="churn-card-header">
                            <span className="window-pill">7-Day Velocity</span>
                            <span className="commits-count">{historicalMetrics.churn7Days.commitCount} Commits</span>
                          </div>
                          <div className="churn-total">{historicalMetrics.churn7Days.totalChurn.toLocaleString()} <span className="churn-unit">lines churned</span></div>
                          <div className="churn-diff">
                            <span className="additions text-success">+{historicalMetrics.churn7Days.additions.toLocaleString()}</span>
                            <span className="deletions text-danger">-{historicalMetrics.churn7Days.deletions.toLocaleString()}</span>
                          </div>
                          <div className="churn-footer">
                            <span>{historicalMetrics.churn7Days.activeAuthors} active author{historicalMetrics.churn7Days.activeAuthors === 1 ? "" : "s"}</span>
                          </div>
                        </div>

                        <div className="churn-card">
                          <div className="churn-card-header">
                            <span className="window-pill">30-Day Window</span>
                            <span className="commits-count">{historicalMetrics.churn30Days.commitCount} Commits</span>
                          </div>
                          <div className="churn-total">{historicalMetrics.churn30Days.totalChurn.toLocaleString()} <span className="churn-unit">lines churned</span></div>
                          <div className="churn-diff">
                            <span className="additions text-success">+{historicalMetrics.churn30Days.additions.toLocaleString()}</span>
                            <span className="deletions text-danger">-{historicalMetrics.churn30Days.deletions.toLocaleString()}</span>
                          </div>
                          <div className="churn-footer">
                            <span>{historicalMetrics.churn30Days.activeAuthors} active author{historicalMetrics.churn30Days.activeAuthors === 1 ? "" : "s"}</span>
                          </div>
                        </div>

                        <div className="churn-card highlight">
                          <div className="churn-card-header">
                            <span className="window-pill highlight">All-Time Lifetime</span>
                            <span className="commits-count">{historicalMetrics.churnAllTime.commitCount} Commits</span>
                          </div>
                          <div className="churn-total">{historicalMetrics.churnAllTime.totalChurn.toLocaleString()} <span className="churn-unit">lines churned</span></div>
                          <div className="churn-diff">
                            <span className="additions text-success">+{historicalMetrics.churnAllTime.additions.toLocaleString()}</span>
                            <span className="deletions text-danger">-{historicalMetrics.churnAllTime.deletions.toLocaleString()}</span>
                          </div>
                          <div className="churn-footer">
                            <span>{historicalMetrics.churnAllTime.activeAuthors} total author{historicalMetrics.churnAllTime.activeAuthors === 1 ? "" : "s"}</span>
                          </div>
                        </div>
                      </div>

                      {/* Contributor Risk Profiling & Bus Factor Intelligence */}
                      {busFactorData && (
                        <div className="bus-factor-section">
                          <h4 className="sub-heading">🧑‍💻 Contributor Risk Profiling & Bus Factor Alert</h4>
                          <p className="sub-desc">
                            Evaluates team resilience, key-person risk, and file knowledge silos where a single developer monopolizes code modifications and security defects.
                          </p>

                          {/* Bus Factor Hero Card */}
                          <div className="bus-factor-banner">
                            <div className="bus-factor-score-card">
                              <span className="bus-factor-label">Repository Bus Factor</span>
                              <div className="bus-factor-number">
                                {busFactorData.overallBusFactor}
                              </div>
                              <span className={getBusFactorStatusClass(busFactorData.busFactorStatus)}>
                                {busFactorData.busFactorStatus.replace(/_/g, " ")}
                              </span>
                            </div>
                            <div className="bus-factor-summary-box">
                              <h4>Key-Person Dependency & Knowledge Silo Assessment</h4>
                              <p>{busFactorData.executiveSummary}</p>
                              <div className="bus-factor-stats-row">
                                <div className="bf-stat">
                                  <span className="bf-stat-label">Core Author Concentration:</span>
                                  <span className="bf-stat-value">{busFactorData.coreAuthorsConcentration}% of Churn</span>
                                </div>
                                <div className="bf-stat">
                                  <span className="bf-stat-label">Identified Knowledge Silos:</span>
                                  <span className="bf-stat-value text-danger">{busFactorData.totalSiloedFiles} Files</span>
                                </div>
                              </div>
                            </div>
                          </div>

                          {/* High-Risk Siloed Files Table */}
                          {busFactorData.highRiskSilos.length > 0 && (
                            <div className="silos-table-card">
                              <div className="silos-table-header">
                                <h5>🚨 High-Risk File Knowledge Silos ({busFactorData.highRiskSilos.length})</h5>
                                <span className="silos-desc">Files where 75%+ of modifications are executed by a single author without co-maintenance</span>
                              </div>
                              <div className="hotspots-table-wrap">
                                <table className="hotspots-table">
                                  <thead>
                                    <tr>
                                      <th>File Path</th>
                                      <th>Dominant Contributor</th>
                                      <th>Monopoly %</th>
                                      <th>Changes</th>
                                      <th>Line Churn</th>
                                      <th>Defects</th>
                                      <th>Silo Risk</th>
                                      <th>Actionable Recommendation</th>
                                    </tr>
                                  </thead>
                                  <tbody>
                                    {busFactorData.highRiskSilos.map((s, idx) => (
                                      <tr key={idx}>
                                        <td className="hotspot-filepath"><code>{s.filePath}</code></td>
                                        <td><strong>{s.dominantAuthor}</strong></td>
                                        <td><span className="dominance-pill">{s.dominancePercentage}%</span></td>
                                        <td>{s.totalCommits}</td>
                                        <td>{s.totalChurn.toLocaleString()}</td>
                                        <td>
                                          {s.defectCount > 0 ? (
                                            <span className="text-danger font-bold">{s.defectCount} ({s.criticalDefects} Crit)</span>
                                          ) : (
                                            <span className="text-muted">0</span>
                                          )}
                                        </td>
                                        <td>
                                          <span className={getSiloBadgeClass(s.siloRiskLevel)}>
                                            {s.siloRiskLevel.replace(/_/g, " ")}
                                          </span>
                                        </td>
                                        <td className="recommendation-cell">
                                          <span className="remediation-text">{s.recommendation}</span>
                                        </td>
                                      </tr>
                                    ))}
                                  </tbody>
                                </table>
                              </div>
                            </div>
                          )}

                          {/* Contributor Profiles & Team Recommendations */}
                          <div className="bus-factor-details-grid">
                            {/* Contributor Risk Matrix */}
                            <div className="contributor-profiles-col">
                              <h5>Contributor Key-Person Dependency Profiles</h5>
                              <div className="contributor-cards-list">
                                {busFactorData.contributorProfiles.map((cp, idx) => (
                                  <div key={idx} className="contributor-profile-card">
                                    <div className="cp-header">
                                      <span className="cp-name">{cp.authorName}</span>
                                      <span className={getSiloBadgeClass(cp.contributorRiskLevel)}>
                                        {cp.contributorRiskLevel.replace(/_/g, " ")}
                                      </span>
                                    </div>
                                    <div className="cp-stats-grid">
                                      <div><span className="text-muted">Share of Churn:</span> <strong>{cp.churnPercentage}%</strong></div>
                                      <div><span className="text-muted">Commits:</span> <strong>{cp.commitCount}</strong></div>
                                      <div><span className="text-muted">Silos Controlled:</span> <strong className={cp.siloedFilesCount > 0 ? "text-danger" : ""}>{cp.siloedFilesCount}</strong></div>
                                      <div><span className="text-muted">Associated Defects:</span> <strong className={cp.defectsAssociated > 0 ? "text-danger" : ""}>{cp.defectsAssociated}</strong></div>
                                    </div>
                                    <div className="cp-reco">{cp.recommendation}</div>
                                  </div>
                                ))}
                              </div>
                            </div>

                            {/* Agile / Team Governance Checklist */}
                            <div className="team-reco-col">
                              <h5>🛡️ Agile Team Governance & Review Directives</h5>
                              <div className="team-reco-card">
                                <ul className="team-reco-list">
                                  {busFactorData.teamRecommendations.map((tr, idx) => (
                                    <li key={idx}>
                                      <span className="check-bullet">✔</span>
                                      <span>{tr}</span>
                                    </li>
                                  ))}
                                </ul>
                              </div>
                            </div>
                          </div>
                        </div>
                      )}

                      {/* File Hotspots Table */}
                      <div className="hotspots-section">
                        <div className="hotspots-header">
                          <div>
                            <h4 className="sub-heading">File Hotspot Intelligence</h4>
                            <p className="sub-desc">
                              Files ranked by change frequency (velocity) and accumulated defect density. Files at the top represent the highest regression and security risks.
                            </p>
                          </div>
                        </div>

                        {historicalMetrics.topHotspots.length === 0 ? (
                          <div className="empty-state-card">
                            <p>No hotspots detected. Repository changes are evenly distributed with zero defect concentration.</p>
                          </div>
                        ) : (
                          <div className="hotspots-table-wrap">
                            <table className="hotspots-table">
                              <thead>
                                <tr>
                                  <th>File Path</th>
                                  <th>Risk Level</th>
                                  <th>Hotspot Score</th>
                                  <th>Changes</th>
                                  <th>Lines Churned</th>
                                  <th>Defects Found</th>
                                  <th>Prescriptive Recommendation</th>
                                </tr>
                              </thead>
                              <tbody>
                                {historicalMetrics.topHotspots.map((h, idx) => (
                                  <tr key={idx} className={`hotspot-row-${h.riskLevel.toLowerCase()}`}>
                                    <td className="hotspot-filepath">
                                      <code>{h.filePath}</code>
                                    </td>
                                    <td>
                                      <span className={getSeverityBadgeClass(h.riskLevel)}>
                                        {h.riskLevel}
                                      </span>
                                    </td>
                                    <td className="hotspot-score-cell">
                                      <strong>{h.hotspotScore}</strong>
                                    </td>
                                    <td>
                                      <span className="change-count-badge">{h.changeCount} commit{h.changeCount === 1 ? "" : "s"}</span>
                                    </td>
                                    <td className="churn-cell">
                                      <span className="text-success">+{h.additions}</span> / <span className="text-danger">-{h.deletions}</span>
                                    </td>
                                    <td>
                                      {h.findingsCount > 0 ? (
                                        <span className="findings-indicator">
                                          {h.criticalFindings > 0 && <span className="finding-chip crit">{h.criticalFindings} Crit</span>}
                                          {h.highFindings > 0 && <span className="finding-chip high">{h.highFindings} High</span>}
                                          {h.mediumFindings > 0 && <span className="finding-chip med">{h.mediumFindings} Med</span>}
                                          {h.lowFindings > 0 && <span className="finding-chip low">{h.lowFindings} Low</span>}
                                        </span>
                                      ) : (
                                        <span className="text-muted">0 findings</span>
                                      )}
                                    </td>
                                    <td className="recommendation-cell">
                                      <span className="remediation-text">{h.recommendation}</span>
                                    </td>
                                  </tr>
                                ))}
                              </tbody>
                            </table>
                          </div>
                        )}
                      </div>

                      {/* Daily Activity Timeline & Top Contributors Grid */}
                      <div className="metrics-columns">
                        {/* Daily Timeline */}
                        <div className="metric-col">
                          <h4 className="sub-heading">Activity & Churn Timeline (Past Commits)</h4>
                          {historicalMetrics.dailyTimeline.length === 0 ? (
                            <p className="text-muted">No timeline data available.</p>
                          ) : (
                            <div className="timeline-list">
                              {historicalMetrics.dailyTimeline.map((d, idx) => (
                                <div key={idx} className="timeline-row">
                                  <div className="timeline-date">{d.date}</div>
                                  <div className="timeline-commits-badge">{d.commitCount} {d.commitCount === 1 ? "commit" : "commits"}</div>
                                  <div className="timeline-churn">
                                    <span className="text-success">+{d.additions}</span> / <span className="text-danger">-{d.deletions}</span>
                                    <span className="churn-bar-indicator">({d.totalChurn} lines)</span>
                                  </div>
                                </div>
                              ))}
                            </div>
                          )}
                        </div>

                        {/* Top Contributors */}
                        <div className="metric-col">
                          <h4 className="sub-heading">Contributor Churn Distribution</h4>
                          {historicalMetrics.topAuthors.length === 0 ? (
                            <p className="text-muted">No author metrics available.</p>
                          ) : (
                            <div className="authors-list">
                              {historicalMetrics.topAuthors.map((a, idx) => (
                                <div key={idx} className="author-card">
                                  <div className="author-info">
                                    <span className="author-name">{a.authorName}</span>
                                    <span className="author-pct">{a.churnPercentage}% of churn</span>
                                  </div>
                                  <div className="author-details">
                                    <span>{a.commitCount} commit{a.commitCount === 1 ? "" : "s"}</span>
                                    <span>
                                      <span className="text-success">+{a.additions}</span> / <span className="text-danger">-{a.deletions}</span>
                                    </span>
                                  </div>
                                  <div className="author-churn-bar-bg">
                                    <div
                                      className="author-churn-bar-fill"
                                      style={{ width: `${Math.min(100, Math.max(8, a.churnPercentage))}%` }}
                                    />
                                  </div>
                                </div>
                              ))}
                            </div>
                          )}
                        </div>
                      </div>
                    </>
                  )}
                </section>
              )}

              {/* TAB 3: BRANCH COMMIT HISTORY */}
              {activeTab === "commits" && (
                <section className="commits-history-section">
                  <div className="section-intro">
                    <h3>Commit History on Branch: {selectedBranch}</h3>
                    <p>Commits ingested for branch '{selectedBranch}'. Each commit's unified diff is inspected for code vulnerabilities.</p>
                  </div>

                  {commits.length === 0 ? (
                    <div className="empty-state-card">
                      <p>No commits ingested yet for branch '{selectedBranch}'. Click "Analyze Active Branch" above.</p>
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
                            <th>Auto-Fix Action</th>
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
                              <td>
                                <button
                                  type="button"
                                  className="btn-quick-fix"
                                  disabled={loadingFixId === f.id}
                                  onClick={() => handleOpenFixModal(f.id)}
                                >
                                  {loadingFixId === f.id ? "Loading..." : "✨ Fix Diff"}
                                </button>
                              </td>
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
