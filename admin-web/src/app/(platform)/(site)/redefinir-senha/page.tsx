import type { Metadata } from "next";
import AuthForm from "@/components/auth-form";

// The token travels in the URL fragment, read in the browser and never sent to servers or logs.
export const metadata: Metadata = { title: "Redefinir senha | Smart Commerce", robots: { index: false, follow: false } };
export default function ResetPasswordPage() { return <AuthForm mode="reset" />; }
