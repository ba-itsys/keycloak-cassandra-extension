# Keycloak Cassandra Community

Keycloak Cassandra is a datastore and caching extension for Keycloak, the Open Source Identity and Access Management
solution for modern Applications and Services.

## Building and working with the codebase

To build the codebase you need an installed JDK of at least version 21 and Maven.
The tests use the Testcontainers-framework to start a local Apache Cassandra database instance. For this to work, you
need Docker installed on your system as well.

## Contributing to Keycloak Cassandra

Keycloak Cassandra welcomes contributions as well as feedback from the community. We do have a few guidelines in place
to help you be successful with your contribution to Keycloak Cassandra.

Firstly, if you want to contribute a larger change we ask that you open a
discussion first. For minor changes you can skip this part and go straight ahead to sending a contribution. Bear in mind
that if you open a discussion first you can identify if the change will be accepted, as well as getting early feedback.

Here's a quick checklist for a good PR, more details below:

1. A discussion around the change
2. A GitHub Issue with a good description associated with the PR
3. One feature/change per PR
4. One commit per PR
5. PR rebased on main (`git rebase`, not `git pull`)
6. [Good descriptive commit message, with link to issue](#commit-messages-and-issue-linking)
7. No changes to code not directly related to your PR
8. Includes test case to demonstrate your change works
9. Includes documentation

Once you have submitted your PR please monitor it for comments/feedback. We reserve the right to close inactive PRs if
you do not respond within 2 weeks (bear in mind you can always open a new PR if it is closed due to inactivity).

### Submitting your PR

When preparing your PR make sure you have a single commit and your branch is rebased on the main branch from the
project repository.

This means use the `git rebase` command and not `git pull` when integrating changes from main to your branch. See
[Git Documentation](https://git-scm.com/book/en/v2/Git-Branching-Rebasing) for more details.

We require that you squash to a single commit. You can do this with the `git rebase -i HEAD~X` command where X
is the number of commits you want to squash. See
the [Git Documentation](https://git-scm.com/book/en/v2/Git-Tools-Rewriting-History)
for more details.

The above helps us review your PR and also makes it easier for us to maintain the repository. It is also required by
our automatic merging process.

Please also provide a good description [commit message, with a link to the issue](#commit-messages-and-issue-linking).
We also require that the commit message includes a link to the
issue ([linking a pull request to an issue](https://docs.github.com/en/issues/tracking-your-work-with-issues/linking-a-pull-request-to-an-issue)).

### Developer's Certificate of Origin

Any contributions to Keycloak Cassandra must only contain code that can legally be contributed to Keycloak, and which
the Keycloak
project can distribute under its license.

Before contributing to Keycloak Cassandra please read
the [Developer's Certificate of Origin](https://developercertificate.org/)
and sign-off all commits with the `--signoff` option provided by `git commit`. For example:

```
git commit --signoff --message "This is the commit message"
```

This option adds a `Signed-off-by` trailer at the end of the commit log message.

## Checks (formatting, lint, tests)

Build and run tests locally:

- `mvn verify`

If formatting fails locally, apply formatting fixes and re-run:

- `mvn spotless:apply`

Please ensure all tests pass locally before opening a PR.

### Commit messages and Releases

This repository uses Conventional Commits (https://www.conventionalcommits.org/).

Please format your commit messages like:

```
<type>(optional scope): short description

[optional body]
[optional footer(s)]

Signed-off-by: Max Mustermann <max-mustermann@example.org>
```

Common types: `feat`, `fix`, `docs`, `refactor`, `test`, `build`, `ci`, `chore`.

Examples:

- `feat(auth): add DPoP validation to login challenge`
- `fix(spi): prevent NPE when no push provider configured`

Releases and changelogs are managed by release-please via GitHub Actions. Do not bump versions or edit CHANGELOG
manually; use proper Conventional Commits and the bot will propose release PRs.

## License

By contributing, you agree that your contributions will be licensed under the Apache License, Version 2.0. See `LICENSE`
for details.
