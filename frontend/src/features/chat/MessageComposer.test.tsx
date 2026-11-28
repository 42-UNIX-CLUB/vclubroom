import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

afterEach(cleanup);
import { MessageComposer } from "./MessageComposer";

describe("Enter while an input method is composing", () => {
  it("does not send a half-typed word", () => {
    const onSend = vi.fn();
    render(<MessageComposer onSend={onSend} />);
    const box = screen.getByRole("textbox", { name: "Write a message" });
    fireEvent.change(box, { target: { value: "ni" } });
    fireEvent.keyDown(box, { key: "Enter", isComposing: true });   // confirming 你 in a Chinese IME
    expect(onSend).not.toHaveBeenCalled();
  });

  it("still sends on a plain Enter", () => {
    const onSend = vi.fn();
    render(<MessageComposer onSend={onSend} />);
    const box = screen.getByRole("textbox", { name: "Write a message" });
    fireEvent.change(box, { target: { value: "hello" } });
    fireEvent.keyDown(box, { key: "Enter" });
    expect(onSend).toHaveBeenCalledWith("hello");
  });
});
