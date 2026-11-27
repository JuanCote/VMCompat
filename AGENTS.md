# VMCompat release workflow

- Development candidates are LOCAL ONLY. Do not upload to Steam/Workshop, publish updates or change Workshop visibility until the user has confirmed that exact candidate works and authorized its release in the conversation. A prior conditional release instruction counts only after its manual acceptance condition is met.
- Component/GL/shader checks do not replace the user testing a world and first-person view. Preserve the public artifact until the user confirms the exact local candidate works and asks to publish it.
- Use the isolated build and component checks by default. For maintainer-authorized gameplay tests, preserve the existing Mod ID and approval workflow, and keep a reversible backup of the installed package. Do not edit game binaries, launch options or approval/trust decisions, or create a duplicate discovered mod.
- Never automatically launch PZ, including through computer-use app selection or snapshots that may reopen it. Use isolated component fixtures, file/process reads and logs. Capture running-process diagnostics only when a process actually exists.
- Record exact candidate SHA and manual acceptance status. Do not claim a freeze fixed from shader compilation alone.
