import { useEffect, useState } from "react";

function App() {
  const [backendStatus, setBackendStatus] = useState("Checking...");

  useEffect(() => {
    fetch("http://localhost:8080/api/health")
      .then((response) => {
        if (!response.ok) {
          throw new Error("Backend is unavailable");
        }

        return response.json();
      })
      .then((data) => {
        if (data.status === "UP") {
          setBackendStatus("Connected");
        } else {
          setBackendStatus("Unavailable");
        }
      })
      .catch(() => {
        setBackendStatus("Disconnected");
      });
  }, []);

  return (
    <main>
      <h1>CodeSure</h1>

      <p>Software Risk Intelligence</p>

      <section>
        <p>Backend: ● {backendStatus}</p>
        <p>Database: ● Pending</p>
      </section>
    </main>
  );
}

export default App;