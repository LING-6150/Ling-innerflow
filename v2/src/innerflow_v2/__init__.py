"""InnerFlow v2 — Memory/Safety Kernel (eval-first).

This is NOT an agent framework and NOT a Python rewrite of Java InnerFlow.
It exists to make two claims measurable before any runtime is built:

1. Cross-session memory can be consolidated and conflict-resolved more
   consistently than full-history context or naive RAG-over-chat.
2. Crisis safety can be enforced as a non-bypassable invariant before any
   LLM / tool / runtime step.

See docs/adr/0001-kernel-not-framework.md for the foundational decision.
"""

__version__ = "0.1.0"
