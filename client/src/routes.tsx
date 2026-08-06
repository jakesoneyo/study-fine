/**
 * 라우트 트리 — RequireAuth(로그인 여부) → AppLayout(공용 셸) → RequireOrganizer(운영자 전용 화면).
 */
import { BrowserRouter, Navigate, Route, Routes } from "react-router";
import { RequireAuth } from "./routes/RequireAuth";
import { RequireOrganizer } from "./routes/RequireOrganizer";
import { AppLayout } from "./components/AppLayout";
import Login from "./pages/Login";
import Dashboard from "./pages/Dashboard";
import Members from "./pages/Members";
import Sessions from "./pages/Sessions";
import SessionCheckIn from "./pages/SessionCheckIn";
import Settings from "./pages/Settings";

export function AppRoutes() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<Login />} />

        <Route element={<RequireAuth />}>
          <Route element={<AppLayout />}>
            <Route index element={<Dashboard />} />

            <Route element={<RequireOrganizer />}>
              <Route path="members" element={<Members />} />
              <Route path="sessions" element={<Sessions />} />
              <Route path="sessions/:id" element={<SessionCheckIn />} />
              <Route path="settings" element={<Settings />} />
            </Route>
          </Route>
        </Route>

        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  );
}
