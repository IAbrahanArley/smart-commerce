import type { Metadata } from "next";
import AuthForm from "@/components/auth-form";

export const metadata: Metadata = { title: "Confirmar e-mail | Smart Commerce", robots: { index: false, follow: false } };
export default function ActivationPage() { return <AuthForm mode="activation" />; }
