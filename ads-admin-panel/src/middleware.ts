export { auth as middleware } from "@/auth";

export const config = {
  // Protect all routes except login and public assets
  matcher: ['/((?!api|_next/static|_next/image|favicon.ico|login).*)'],
};
