package domain

import java.util.Date

case class User(id: String, lang: String, followersCount: Int)

case class Tweet(id: String, user: User, text: String, createdAt: Date)
