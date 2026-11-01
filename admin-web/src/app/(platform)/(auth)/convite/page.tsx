import type { Metadata } from "next";
import InvitationAccept from "@/components/invitation-accept";

// SC-049: the token travels in the URL fragment, read in the browser and never sent to servers or logs.
export const metadata: Metadata = { title: "Convite para a equipe | Smart Commerce", robots: { index: false, follow: false } };
export default function InvitationPage() { return <InvitationAccept />; }
