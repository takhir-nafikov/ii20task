package org.ufku.ii20task.mcp

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PullRequest(
    val id: Long,
    val number: Int,
    val state: String,
    val locked: Boolean,
    val title: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    val user: User,
    val draft: Boolean,
    val url: String,
    @SerialName("html_url") val htmlUrl: String,
    @SerialName("issue_url") val issueUrl: String,
    @SerialName("statuses_url") val statusesUrl: String,
    @SerialName("diff_url") val diffUrl: String,
    @SerialName("patch_url") val patchUrl: String,
    @SerialName("commits_url") val commitsUrl: String,
    @SerialName("comments_url") val commentsUrl: String,
    @SerialName("review_comments_url") val reviewCommentsUrl: String,
    @SerialName("review_comment_url") val reviewCommentUrl: String,
    @SerialName("author_association") val authorAssociation: String,
    @SerialName("node_id") val nodeId: String,
    val merged: Boolean,
    val mergeable: Boolean? = null,
    @SerialName("mergeable_state") val mergeableState: String,
    val rebaseable: Boolean? = null,
    @SerialName("merge_commit_sha") val mergeCommitSha: String? = null,
    val comments: Int,
    val commits: Int,
    val additions: Int,
    val deletions: Int,
    @SerialName("changed_files") val changedFiles: Int,
    @SerialName("maintainer_can_modify") val maintainerCanModify: Boolean,
    @SerialName("review_comments") val reviewComments: Int,
    @SerialName("_links") val links: Links,
    val head: BranchInfo,
    val base: BranchInfo
)

@Serializable
data class User(
    val login: String,
    val id: Long,
    @SerialName("node_id") val nodeId: String,
    @SerialName("avatar_url") val avatarUrl: String,
    @SerialName("html_url") val htmlUrl: String,
    @SerialName("gravatar_id") val gravatarId: String,
    val type: String,
    @SerialName("site_admin") val siteAdmin: Boolean,
    val url: String,
    @SerialName("events_url") val eventsUrl: String,
    @SerialName("following_url") val followingUrl: String,
    @SerialName("followers_url") val followersUrl: String,
    @SerialName("gists_url") val gistsUrl: String,
    @SerialName("organizations_url") val organizationsUrl: String,
    @SerialName("received_events_url") val receivedEventsUrl: String,
    @SerialName("repos_url") val reposUrl: String,
    @SerialName("starred_url") val starredUrl: String,
    @SerialName("subscriptions_url") val subscriptionsUrl: String
)

@Serializable
data class Links(
    val self: LinkItem,
    val html: LinkItem,
    val issue: LinkItem,
    val comments: LinkItem,
    @SerialName("review_comments") val reviewComments: LinkItem,
    @SerialName("review_comment") val reviewComment: LinkItem,
    val commits: LinkItem,
    val statuses: LinkItem
)

@Serializable
data class LinkItem(
    val href: String
)

@Serializable
data class BranchInfo(
    val label: String,
    val ref: String,
    val sha: String,
    val repo: Repo,
    val user: User
)

@Serializable
data class Repo(
    val id: Long,
    @SerialName("node_id") val nodeId: String,
    val owner: User,
    val name: String,
    @SerialName("full_name") val fullName: String,
    @SerialName("default_branch") val defaultBranch: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("pushed_at") val pushedAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("html_url") val htmlUrl: String,
    @SerialName("clone_url") val cloneUrl: String,
    @SerialName("git_url") val gitUrl: String,
    @SerialName("ssh_url") val sshUrl: String,
    @SerialName("svn_url") val svnUrl: String,
    val language: String? = null,
    val fork: Boolean,
    @SerialName("forks_count") val forksCount: Int,
    @SerialName("open_issues_count") val openIssuesCount: Int,
    @SerialName("open_issues") val openIssues: Int,
    @SerialName("stargazers_count") val stargazersCount: Int,
    @SerialName("watchers_count") val watchersCount: Int,
    val watchers: Int,
    val size: Int,
    @SerialName("allow_forking") val allowForking: Boolean,
    @SerialName("web_commit_signoff_required") val webCommitSignoffRequired: Boolean,
    val archived: Boolean,
    val disabled: Boolean,
    val private: Boolean,
    @SerialName("has_issues") val hasIssues: Boolean,
    @SerialName("has_wiki") val hasWiki: Boolean,
    @SerialName("has_pages") val hasPages: Boolean,
    @SerialName("has_projects") val hasProjects: Boolean,
    @SerialName("has_downloads") val hasDownloads: Boolean,
    @SerialName("has_discussions") val hasDiscussions: Boolean,
    @SerialName("is_template") val isTemplate: Boolean,
    val url: String,
    @SerialName("archive_url") val archiveUrl: String,
    @SerialName("assignees_url") val assigneesUrl: String,
    @SerialName("blobs_url") val blobsUrl: String,
    @SerialName("branches_url") val branchesUrl: String,
    @SerialName("collaborators_url") val collaboratorsUrl: String,
    @SerialName("comments_url") val commentsUrl: String,
    @SerialName("commits_url") val commitsUrl: String,
    @SerialName("compare_url") val compareUrl: String,
    @SerialName("contents_url") val contentsUrl: String,
    @SerialName("contributors_url") val contributorsUrl: String,
    @SerialName("deployments_url") val deploymentsUrl: String,
    @SerialName("downloads_url") val downloadsUrl: String,
    @SerialName("events_url") val eventsUrl: String,
    @SerialName("forks_url") val forksUrl: String,
    @SerialName("git_commits_url") val gitCommitsUrl: String,
    @SerialName("git_refs_url") val gitRefsUrl: String,
    @SerialName("git_tags_url") val gitTagsUrl: String,
    @SerialName("hooks_url") val hooksUrl: String,
    @SerialName("issue_comment_url") val issueCommentUrl: String,
    @SerialName("issue_events_url") val issueEventsUrl: String,
    @SerialName("issues_url") val issuesUrl: String,
    @SerialName("keys_url") val keysUrl: String,
    @SerialName("labels_url") val labelsUrl: String,
    @SerialName("languages_url") val languagesUrl: String,
    @SerialName("merges_url") val mergesUrl: String,
    @SerialName("milestones_url") val milestonesUrl: String,
    @SerialName("notifications_url") val notificationsUrl: String,
    @SerialName("pulls_url") val pullsUrl: String,
    @SerialName("releases_url") val releasesUrl: String,
    @SerialName("stargazers_url") val stargazersUrl: String,
    @SerialName("statuses_url") val statusesUrl: String,
    @SerialName("subscribers_url") val subscribersUrl: String,
    @SerialName("subscription_url") val subscriptionUrl: String,
    @SerialName("tags_url") val tagsUrl: String,
    @SerialName("trees_url") val treesUrl: String,
    @SerialName("teams_url") val teamsUrl: String,
    val visibility: String
)


@Serializable
data class PullRequestDiffOnly(
    @SerialName("diff_url")
    val diffUrl: String
)