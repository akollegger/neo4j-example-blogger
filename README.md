neo4j-example-blogger
=====================
A simple blogging service, written in scala and using neo4j for persistence. 

A Tour of the Model
-------------------
![Domain Model](neo4j-example-blogger/raw/master/site/images/model.png)

###Blog Service

The blogging service itself.

- `manages` User
  - user creation
  - finds existing Users
- `knows about` Blog
  - finds existing Blogs

###User

Users of the blogging system, Users create Blogs and Articles.

- `can admin` Blog
  - blog administrator
  - initial creator of a blog is automatically an admin
  - an admin can grant admin privileges to other users
  - an admin can grant publish access to other users
- `can publish to` Blog
  - allowed to publish articles to the blog
  - initial creator of a blog is automatically allowed to publish to that blog
  - a user can publish to multiple blogs
- `is owner` (of) Article
  - an article can only have one owner
- `is author` (of) Article
  - an Article can have many authors
- `is working on` WorkingCopy
  - for every Article, a user has a personal WorkingCopy

###Blog

A place to publish Articles. Any User who "can publish to" is allowed to publish
an Article to a Blog, as long as there are no un-committed WorkingCopies.

- Article is `published in`
  The direction here could easily be reversed. 

###Article

Some engrossing reading material written by one or more Users. Articles can
have multiple revisions.

- `next version` (of) Article
  - a more recent version of an Article
  - enables a linked-list of Articles
- `published in` Blog
  - each User can publish an Article in any Blog to which they `can publish`
    This allows a single article to first appear in, say, "Kolleger Family Times"
    and then later get picked up by "National Geographic". 

###Working Copy

An editable version of an Article, allowing Users to work on independently,
merging changes upon commit. Yeah, light-weight version control that would
allow for collaborative editing (if actual diffs were used). 

- User `is working on`
  - personal copy for each User
- `copy of` Article
  - revision from which this copy started


