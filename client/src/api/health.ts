export interface HealthResponse { status: string; service: string; timestamp: number }

export async function checkHealth(): Promise<HealthResponse> {
  const response = await fetch('/api/health')
  if (!response.ok) throw new Error(`Backend returned HTTP ${response.status}`)
  return response.json() as Promise<HealthResponse>
}
