import { zodResolver } from "@hookform/resolvers/zod";
import { useQueryClient } from "@tanstack/react-query";
import { useEffect } from "react";
import { useForm } from "react-hook-form";
import { Navigate, useSearchParams } from "react-router";
import { z } from "zod";
import { api } from "@/shared/api/client";
import { ApiError } from "@/shared/api/errors";
import { safeReturnTo } from "@/shared/lib/safeReturnTo";
import { Button } from "@/shared/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/shared/ui/card";
import { Field, FieldError, FieldGroup, FieldLabel } from "@/shared/ui/field";
import { FullPageSpinner } from "@/shared/ui/FullPageSpinner";
import { Input } from "@/shared/ui/input";
import { Separator } from "@/shared/ui/separator";
import { rememberReturnTo, takeReturnTo } from "./returnTo";
import { useMe } from "./useMe";

const loginSchema = z.object({
  username: z.string().trim().min(1, "Enter your username."),
  password: z.string().min(1, "Enter your password."),
});

export function LoginPage() {
  const { data: me, isPending } = useMe();
  const [params] = useSearchParams();
  const returnTo = safeReturnTo(params.get("returnTo"));

  // A failed Login with 42 comes back here (/login?error): forget the page it was meant to resume.
  useEffect(() => {
    takeReturnTo();
  }, []);

  if (isPending) return <FullPageSpinner />;
  // Logged in already, or just now through the form below (it puts the user in the ["me"] cache).
  if (me) return <Navigate to={returnTo} replace />;

  function loginWith42() {
    rememberReturnTo(returnTo);   // the backend always returns to "/", PostLoginRedirect resumes from there
    window.location.assign("/oauth2/authorization/fortytwo");   // full-page navigation, not fetch
  }

  return (
    <div className="mx-auto max-w-sm py-10">
      <Card>
        <CardHeader>
          <CardTitle className="text-xl">Log in to Virtual Clubroom</CardTitle>
          <CardDescription>
            42 students use their intra account. Staff and partners use the account an admin created for them.
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-6">
          {params.has("error") && (
            <p role="alert" className="rounded-lg bg-destructive/10 px-3 py-2 text-sm text-destructive">
              Login with 42 did not complete. Please try again.
            </p>
          )}
          <Button size="lg" className="w-full" onClick={loginWith42}>
            Login with 42
          </Button>
          <div className="flex items-center gap-3 text-xs text-muted-foreground">
            <Separator className="flex-1" />
            or
            <Separator className="flex-1" />
          </div>
          <LocalLoginForm />
        </CardContent>
      </Card>
    </div>
  );
}

function LocalLoginForm() {
  const queryClient = useQueryClient();
  const form = useForm({ resolver: zodResolver(loginSchema), defaultValues: { username: "", password: "" } });
  const { errors, isSubmitting } = form.formState;

  const onSubmit = form.handleSubmit(async (credentials) => {
    try {
      const { data } = await api.POST("/api/auth/login", { body: credentials });
      queryClient.setQueryData(["me"], data ?? null);   // LoginPage re-renders and redirects to returnTo
    } catch (error) {
      form.setError("root", {
        message: error instanceof ApiError && error.status === 401
          ? "Invalid username or password."
          : "Login failed. Please try again.",
      });
    }
  });

  return (
    <form onSubmit={onSubmit} noValidate>
      <FieldGroup>
        <Field data-invalid={!!errors.username}>
          <FieldLabel htmlFor="username">Username</FieldLabel>
          <Input id="username" autoComplete="username" aria-invalid={!!errors.username} {...form.register("username")} />
          <FieldError errors={[errors.username]} />
        </Field>
        <Field data-invalid={!!errors.password}>
          <FieldLabel htmlFor="password">Password</FieldLabel>
          <Input
            id="password"
            type="password"
            autoComplete="current-password"
            aria-invalid={!!errors.password}
            {...form.register("password")}
          />
          <FieldError errors={[errors.password]} />
        </Field>
        <FieldError errors={[errors.root]} />
        <Button type="submit" variant="outline" disabled={isSubmitting}>
          {isSubmitting ? "Logging in…" : "Log in with password"}
        </Button>
      </FieldGroup>
    </form>
  );
}