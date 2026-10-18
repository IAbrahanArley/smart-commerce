import type { Metadata } from "next";
import AuthForm from "@/components/auth-form";

export const metadata: Metadata = { title: "Criar conta | Smart Commerce" };
export default function RegistrationPage() { return <AuthForm mode="registration" />; }
