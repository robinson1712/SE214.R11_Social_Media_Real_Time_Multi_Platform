# Hợp đồng backend cho frontend-web — 24/10/2026

Phạm vi: kiểm kê chỉ đọc controller, DTO, entity/document, service, gateway và repository Flutter hiện có. Đây là hành vi từ mã nguồn, chưa phải kết quả gọi API thực tế. Không đọc `.runtime/env.ps1`. Chỉ tạo tài liệu này, không sửa triển khai.

## 1. Quy ước chung và danh tính

- Gateway native mặc định `http://localhost:8080`, frontend origin mặc định `http://localhost:3001`; cấu hình thực tế có thể ghi đè qua môi trường.
- REST cần `Authorization: Bearer <accessToken>`. Gateway xác thực rồi ghi đè `X-User-Id`, `X-User-Roles`. Browser socket dùng query `?token=<accessToken>`; đây là `token`, không phải `access_token`.
- Response chuẩn: `{success:boolean,message:string,data?:T,timestamp:ISOInstant}`; `@JsonInclude(NON_NULL)` khiến `data` có thể bị bỏ khi null, kể cả GET `/me` của reaction/saved không có bản ghi. Mutation trả Void thường HTTP 200; create post/comment/story/reel/group/conversation/report trả 201.
- Phân trang `P<T> = {content:T[],page:number,size:number,totalElements:number,totalPages:number,last:boolean}`. Query page bắt đầu 0, size mặc định 20. Controller dùng Pageable còn nhận `sort=field,asc|desc`.
- `GET /api/feed/me?page=0&size=20` trả `ApiResponse<PostDto[]>`, không có `P`. `GET /api/users/{id}/friend-ids` trả thẳng `string[]`, không có envelope.
- ID quan hệ: `AuthResponse.accountId` là ID profile/user, dùng cho `authorId`, `senderId`, `participantIds`, `requesterId`, `targetOwnerId`. `friendshipId`, `conversationId`, `commentId`, `savedItem.id`, `media.id` là ID bản ghi riêng; không thay nhau bằng display name.
- `AuthResponse={accountId,email,accessToken,refreshToken}`. `Person` UI hiện thiếu ID; tên không phải khóa ổn định.

## 2. Profile, bạn bè, follow

`UserProfile={id,fullName,avatarUrl,coverUrl,bio,dob,gender,location,workplace,readReceiptsEnabled,createdAt,updatedAt}`; dob `YYYY-MM-DD`; gender `MALE|FEMALE|OTHER`. Các chuỗi/timestamp có thể null theo dữ liệu.

| Method + path | Request / query | data |
|---|---|---|
| GET `/api/users/me`, `/api/users/{id}` | — | UserProfile |
| PUT `/api/users/me` | `{fullName?,bio?,dob?,gender?,location?,workplace?,readReceiptsEnabled?}` | UserProfile |
| PUT `/api/users/me/avatar`, `/api/users/me/cover` | `{mediaUrl:uploadedUrl}` | UserProfile |
| GET `/api/users/search` | `q,page,size,sort?` | P<UserProfile> |
| POST `/api/users/{targetId}/friend-request` | no body | Friendship |
| PUT `/api/users/friend-requests/{friendshipId}/accept` | no body | Friendship |
| PUT `/api/users/friend-requests/{friendshipId}/decline` | no body | Friendship |
| DELETE `/api/users/friends/{friendId}` | friendId = user ID | Void |
| GET `/api/users/me/friends` | page,size,sort? | P<UserProfile> |
| GET `/api/users/me/friend-requests` | page,size,sort? | P<Friendship>, chỉ pending incoming |
| GET `/api/users/{targetId}/friendship-status` | — | `{status,friendshipId}` |
| GET `/api/users/me/suggestions` | `limit=10` | `{profile:UserProfile,mutualFriendCount}[]` |
| DELETE `/api/users/me/suggestions/{targetId}` | — | Void |
| POST / DELETE `/api/users/{targetId}/follow` | no body | Void |
| GET `/api/users/{targetId}/follow-status` | — | `{following:boolean,followerCount,followingCount}` |
| POST / DELETE `/api/users/{targetId}/block` | no body | Void |
| GET `/api/users/me/blocked` | page,size,sort? | P<UserProfile> |

`Friendship={id,requesterId,addresseeId,status,createdAt,respondedAt}`. Entity status `PENDING|ACCEPTED|DECLINED`; relationship-status là `NONE|PENDING_SENT|PENDING_RECEIVED|FRIENDS`. Accept chỉ addressee; decline cả addressee lẫn requester đều gọi được, nên dùng để hủy lời mời outgoing. Decline trả snapshot DECLINED rồi xóa record, cho phép gửi lại. Accept tự follow hai chiều; unfollow và unfriend độc lập (unfriend không bỏ follow).

`follow-status.following` là current user có follow target không; hai count đều thuộc target. Không có REST endpoint liệt kê toàn bộ following/followers trong controller hiện tại: frontend có thể nạp status theo tập profile đã biết, nhưng không được gọi đó là toàn bộ following nếu chưa có danh sách nguồn đầy đủ.

## 3. Feed và bài viết

`Post={id,authorId,content,mediaUrls:string[],privacy,customAudienceUserIds:string[],taggedUserIds:string[],groupId,pageId,sharedPostId,sharedReelId,commentCount,reactionCount,pinned,shareCount,createdAt,updatedAt}`.

`PostDto` từ feed có cùng các trường hiển thị chính nhưng không khai báo `customAudienceUserIds`, `sharedReelId`, `updatedAt`. Shared post/reel cần lấy riêng theo ID, không được embed trong backend response.

| Method + path | Body / query | data |
|---|---|---|
| GET `/api/feed/me` | page,size | PostDto[] |
| POST `/api/feed/seen` | `{postIds:string[]}` | Void |
| POST `/api/posts` | `{content?,mediaUrls?,privacy?,groupId?,pageId?,customAudienceUserIds?,taggedUserIds?}` | Post |
| GET `/api/posts/{id}` | — | Post, kiểm tra visibility |
| PUT `/api/posts/{id}` | `{content?,mediaUrls?,privacy?,customAudienceUserIds?,taggedUserIds?}` | Post |
| DELETE `/api/posts/{id}` | — | Void |
| GET `/api/posts/author/{authorId}` | page,size | P<Post>, pinned desc rồi createdAt desc |
| GET `/api/posts/group/{groupId}`, `/api/posts/page/{pageId}` | page,size | P<Post>, createdAt desc |
| GET `/api/posts/search` | q,page,size | P<Post>, chỉ public visible group content |
| GET `/api/posts/batch` | `ids=id1,id2,...` | Post[] đã lọc visibility |
| PUT / DELETE `/api/posts/{id}/pin` | no body | Post |
| POST `/api/posts/{id}/share` | `{content?,privacy?}` | Post mới chứa sharedPostId |
| POST `/api/posts/share-reel/{reelId}` | `{content?,privacy?}` | Post mới chứa sharedReelId |

Privacy `PUBLIC|FRIENDS|CUSTOM|PRIVATE`; create mặc định PUBLIC. UI `Public→PUBLIC`, `Friends→FRIENDS`, `Only Me→PRIVATE`. CUSTOM cần allow-list không rỗng. Create cần content không trắng hoặc mediaUrls không rỗng. Update null nghĩa giữ nguyên; xóa media dùng `[]`. Bài group cần membership APPROVED, bài fanpage cần quyền page tương ứng. Không có trường `location`, `theme`, `time` hiển thị trong backend Post: location composer hiện không được backend lưu, theme là trình bày, time tính từ createdAt.

## 4. Bình luận, reaction, saved, reports

TargetType dùng chung `POST|COMMENT|REEL|STORY`. Comment chỉ nhận target `POST|REEL|STORY`; reply dùng parentCommentId, không dùng `targetType:COMMENT`.

| Method + path | Body / query | data |
|---|---|---|
| POST `/api/comments` | `{targetType,targetId,targetOwnerId,content,parentCommentId?}` | Comment |
| GET `/api/comments` | targetType,targetId,page,size | P<Comment> top-level mới nhất trước |
| GET `/api/comments/{id}/replies` | page,size | P<Comment> replies cũ nhất trước |
| PUT `/api/comments/{id}` | `{content}` | Comment |
| DELETE `/api/comments/{id}` | — | Void |
| PUT `/api/reactions` | `{targetType,targetId,targetOwnerId?,type}` | Reaction |
| DELETE `/api/reactions` | targetType,targetId | Void |
| GET `/api/reactions/me` | targetType,targetId | Reaction hoặc data vắng |
| GET `/api/reactions/summary` | targetType,targetId | `{counts:{LIKE?:number,...},total:number}` |
| PUT `/api/saved` | `{targetType,targetId,targetOwnerId?}` | SavedItem |
| DELETE `/api/saved` | targetType,targetId | Void |
| GET `/api/saved/me` | targetType,targetId | SavedItem hoặc data vắng |
| GET `/api/saved/mine` | page,size | P<SavedItem> mới nhất trước |
| POST `/api/moderation/reports` | `{targetType,targetId,reason,description?}` | Report |
| GET `/api/moderation/reports/mine` | page,size,sort? | P<Report> |

`Comment={id,targetType,targetId,authorId,targetOwnerId,parentCommentId,content,createdAt,updatedAt,deleted}`. content và targetOwnerId bắt buộc khi create; backend xác minh target và lưu owner thật. Reply parent phải cùng target. Chỉ author được edit/delete; delete là soft delete một record, không có cascade xóa con trong service. UI hiện cho chủ bài xóa bình luận người khác + xóa cả descendant: phải chỉnh để khớp quyền/backend.

ReactionType `LIKE|LOVE|HAHA|WOW|SAD|ANGRY` (UI lowercase cần uppercase khi gửi). `Reaction={id,targetType,targetId,targetOwnerId,userId,type,createdAt}`; unique theo targetType,targetId,userId. PUT thay reaction, không phải toggle; bấm lại reaction đang chọn cần DELETE. `counts` chỉ có loại đã có dữ liệu, loại thiếu tính 0. `SavedItem` cùng shape bỏ trường type; PUT idempotent. Reaction/saved resolve owner từ target, không tin request targetOwnerId; comments vẫn bắt gửi targetOwnerId nonblank. Saved list chứa tham chiếu, cần fetch Post/Reel tương ứng.

`ReportReason=SPAM|HARASSMENT|HATE_SPEECH|NUDITY|VIOLENCE|MISINFORMATION|OTHER`. `ReportTargetType=POST|COMMENT|REEL|STORY|USER|GROUP|FANPAGE`. `Report={id,reporterId,targetType,targetId,reason,description,status,reviewedBy,reviewedAt,resolutionNote,createdAt}`; status `PENDING|DISMISSED|ACTION_TAKEN`. UI LocalReport.details→description, postId→targetId và targetType POST. Admin endpoints: GET `/api/moderation/reports?status=&targetType=&page=&size=`, GET `/api/moderation/reports/{id}`, PUT `/{id}/resolve` body `{action:DISMISS|REMOVE_CONTENT,note?}`.

## 5. Chat, thông báo và STOMP

### Chat REST

| Method + path | Request | data |
|---|---|---|
| POST `/api/chat/conversations` | `{participantIds:string[]}` | Conversation |
| GET `/api/chat/conversations` | — | Conversation[] mới nhất trước |
| GET `/api/chat/conversations/{id}/messages` | page,size | P<Message> sentAt desc |
| POST `/api/chat/conversations/{id}/read` | no body | Void |
| DELETE `/api/chat/messages/{id}` | — | Message soft deleted |
| GET `/api/chat/presence/{userId}` | — | `{online:boolean}` |
| POST `/api/chat/presence/batch` | `{userIds:string[]}` | `{[userId]:boolean}` |

`Conversation={id,type:PRIVATE|GROUP,participantIds,lastMessagePreview,lastMessageAt,createdAt}`. Create tự thêm current user, dedup participants; đúng 2 user dùng PRIVATE và tái sử dụng conversation có sẵn; trường hợp còn lại tạo GROUP. Conversation không có unread count. `Message={id,conversationId,senderId,content,mediaUrl,storyReplyId,storyReplyPreviewUrl,sentAt,readBy:string[],deleted:boolean}`. UI own = senderId===accountId. Messages REST newest-first nên reverse để render timeline. Read đánh dấu user vào readBy tất cả message. Delete chỉ sender, xóa content/mediaUrl. Unread cần suy ra từ incoming chưa có current user trong readBy; nếu chỉ tải page đầu thì chỉ là số quan sát được.

### STOMP thật và REST fallback

- Chat SockJS endpoint `/ws`; native browser WebSocket transport là `ws://localhost:8080/ws/websocket?token=<JWT>` (wss khi HTTPS). Flutter `frontend/lib/core/ws/stomp_service.dart` đang dùng đúng đường này. Có thể dùng WebSocket native + STOMP text frames, tránh thêm thư viện khi đủ nhu cầu hiện tại.
- CONNECT STOMP rồi SUBSCRIBE `/user/queue/messages`; SEND `/app/chat.send`, content-type `application/json`, JSON `{conversationId,content?,mediaUrl?,storyReplyId?,storyReplyPreviewUrl?}`. Server push Message trực tiếp (không ApiResponse) tới cả sender và các participant; reconcile theo message.id để không nhân đôi.
- Send cần text không trắng hoặc mediaUrl không trắng; content tối đa 20.000 ký tự, conversationId/storyReplyId tối đa 128, mediaUrl/storyReplyPreviewUrl tối đa 2048. Guard chỉ nhận SEND destination trên, subscription `/user/queue/messages`; giới hạn mặc định 5 connections/user, 5 subscriptions/connection (duplicate destination bị từ chối), burst 20 rồi 1 send/second, max frame 65.536 bytes.
- **Không có REST endpoint gửi tin** trong backend hiện tại. REST fallback hỗ trợ tải danh sách, lịch sử, mark-read, delete, presence. Khi socket mất, tiếp tục REST poll để nhận/xem lịch sử và giữ draft; chỉ báo gửi thành công sau khi có server message. Không tạo giả POST `/messages`.
- SharePost UI hiện gửi object structured sharedPost vào local conversation. ChatSendRequest không có sharedPost. Cách giữ chức năng với contract hiện tại: gửi text + URL route bài viết qua content và render nhận dạng link, hoặc share vào feed bằng POST `/api/posts/{id}/share`; không nhầm 2 hành động này.
- Reply story: create/reuse PRIVATE conversation với author rồi SEND storyReplyId/storyReplyPreviewUrl và content qua chat.

### Notification

`Notification={id,recipientId,actorId,type,targetType,targetId,message,read,createdAt}`; type `FRIEND_REQUEST|COMMENT|REACTION|GROUP|MATCH|MESSAGE|TAG|FOLLOW`. actorId cần lookup profile, targetId dùng điều hướng theo targetType. Có Mongo TTL 8 tuần.

| Method + path | data |
|---|---|
| GET `/api/notifications/me?page=0&size=20` | P<Notification> createdAt desc |
| GET `/api/notifications/unread-count` | `{count:number}` |
| PUT `/api/notifications/{id}/read` | Notification |
| PUT `/api/notifications/read-all` | Void |
| DELETE `/api/notifications/{id}` | Void |

Notification socket `/ws-notifications/websocket?token=<JWT>`, SUBSCRIBE `/user/queue/notifications`; push trực tiếp Notification. Không được SEND trên socket notification. Guard mặc định 5 connections/user, 1 subscription/connection, max frame 16.384 bytes. REST polling `/me` + `/unread-count` là fallback đầy đủ cho nhận thông báo. UI hiện tự tổng hợp notification từ conversation + friend request; khi tích hợp cần dùng server Notification để giữ ID và read thực.

## 6. Explore, nhóm và tìm kiếm

Backend không có Topic entity/endpoint. UI Explore hiện `Category={name,image,title,description}`, `Topic={id,category,title,author,body,replies:string[]}`, joined là tên category. Tái dùng Group + Post + Comment là đường tích hợp ngắn nhất: Category map group.id/name/coverUrl/description; Topic map group Post; replies map Comment. Group Post không có title riêng: có thể lưu title và body trong content theo một quy ước nhỏ được document, hoặc UI rút dòng đầu làm title. Không gửi `{title,body,category}` trực tiếp vào API Post.

| Method + path | Request / query | data |
|---|---|---|
| GET `/api/groups` | name?,page,size,sort? | P<Group> PUBLIC + PRIVATE đã APPROVED |
| GET `/api/groups/me` | page,size,sort? | P<Group> chỉ APPROVED |
| GET `/api/groups/{id}` | — | Group |
| POST `/api/groups` | `{name,description?,privacy:PUBLIC|PRIVATE}` | Group |
| POST `/api/groups/{id}/join` | no body | GroupMember |
| DELETE `/api/groups/{id}/leave` | — | Void |
| GET `/api/groups/{id}/members` | page,size,sort? | P<GroupMember> chỉ APPROVED |
| PUT `/api/groups/{id}/members/{userId}/approve` | — | GroupMember |
| DELETE `/api/groups/{id}/members/{userId}` | — | Void |
| PUT `/api/groups/{id}/members/{userId}/role` | `{role:ADMIN|MODERATOR|MEMBER}` | GroupMember |
| GET `/api/search` | q bắt buộc nonblank, limit=5 | SearchResults |

`Group={id,name,description,avatarUrl,coverUrl,privacy,ownerId,memberCount,createdAt}`. `GroupMember={id,groupId,userId,role,status:PENDING|APPROVED,joinedAt}`. PUBLIC join APPROVED ngay, PRIVATE join PENDING (không đặt joined=true trước approval). Duplicate join trả conflict; leave xử lý cả pending lẫn approved. Group create tạo owner ADMIN APPROVED. Không có group update art endpoint trong controller.

`SearchResults={users:[{id,fullName,avatarUrl}],groups:[{id,name,avatarUrl,description}],pages:[{id,name,avatarUrl,category}],posts:[{id,authorId,content,createdAt}]}`. Search liên hợp giới hạn mỗi loại, không có pagination tổng; từng service lỗi có thể degrade section sang []. Post search chỉ PUBLIC và không làm lộ content nhóm private.

## 7. Stories, reels, upload

| Method + path | Request / query | data |
|---|---|---|
| GET `/api/stories/feed` | — | Story[] (current user + friends, active) |
| GET `/api/stories/author/{authorId}` | — | Story[] active, author/self/friend visibility |
| POST `/api/stories` | `{mediaUrl,mediaType:IMAGE|VIDEO,caption?,textOverlays?}` | Story |
| POST `/api/stories/{id}/view` | no body | Story |
| DELETE `/api/stories/{id}` | — | Void |
| GET `/api/reels/feed` | page,size,sort? | P<Reel> |
| GET `/api/reels/author/{authorId}` | page,size,sort? | P<Reel> |
| GET `/api/reels/{id}` | — | Reel |
| POST `/api/reels` | `{videoUrl,thumbnailUrl?,caption?}` | Reel |
| POST `/api/reels/{id}/view` | — | Reel |
| POST `/api/reels/{id}/share-count` | — | Reel |
| DELETE `/api/reels/{id}` | — | Void |
| POST `/api/media/upload` | multipart `file`, `purpose` | `{id,url}` |
| GET `/api/media/{id}` | media record ID | `{id,ownerId,url,contentType,purpose,sizeBytes,createdAt}` |
| DELETE `/api/media/{id}` | media record ID, owner only | Void |

`Story={id,authorId,mediaUrl,mediaType,caption,viewerIds:string[],textOverlays,createdAt,expiresAt}`. Expiry 24h; không có GET Story by id. Text overlay `{text,fontFamily?,color?,x:number,y:number,fontSize?}`; x/y fractional 0..1 theo thiết kế, fontSize mặc định 24. View dùng server viewerIds; heart dùng Reaction STORY/LOVE; reply dùng chat. UI hiện stories hằng trong viewers.tsx và seenStories/storyLikes local; cần thay bằng data server.

`Reel={id,authorId,videoUrl,thumbnailUrl,caption,viewCount,commentCount,reactionCount,shareCount,createdAt}`; comments/reactions/saved dùng targetType REEL. Share vào feed dùng `/api/posts/share-reel/{id}` và post-service đã gọi tăng share-count qua Feign, không gọi thêm share-count lần nữa cho cùng hành động. Endpoint share-count riêng dùng khi chỉ chia sẻ ngoài luồng tạo Post. Reels hiện public; feed xếp hạng từ candidate pool theo engagement + time decay, không đơn thuần sort mới nhất. UI types hiện chưa có Story/Reel; cần thêm DTO/view model tối thiểu nếu triển khai màn tương ứng.

MediaPurpose `AVATAR|COVER|POST|STORY|REEL|CHAT|PAGE|GROUP` (parse trim + uppercase). Upload trả URL để gửi vào các DTO, không gửi media.id vào mediaUrl. Profile/Page/Group art chỉ ảnh <=10MiB; các mục khác image/video theo servlet limit mặc định 50MB. CHAT thêm audio/document. MIME ảnh `image/jpeg|png|gif|webp`; video `video/mp4|webm|quicktime`; audio chat `audio/webm|ogg|mpeg|mp4|wav|x-wav`; documents chat PDF, Word, Excel, PowerPoint, zip, text/plain. Upload POST không chấp nhận document; UI composer hiện cho chọn document cần giới hạn theo nơi dùng. Blob URL chỉ preview cục bộ, phải upload File/Blob trước publish/send.

## 8. Các điều chỉnh tối thiểu cần thực hiện ở frontend-web

1. Bổ sung ID thật vào Person/PostComment/Message/Conversation và giữ presentation name riêng; route/key quan hệ dùng ID. Đổi name settings chỉ sửa profile, không cần viết lại tất cả bài/chat như local demo hiện tại.
2. Viết API adapter bóc envelope + P đúng từng endpoint; hydrate profile, author, comments/replies, reactions, saved, groups, notification. Giữ theme/draft/animation là local UI; không để snapshot IndexedDB demo ghi đè dữ liệu server. Validator social-storage hiện chỉ chấp nhận asset/blob URLs và avatar filename nên không thể dùng nguyên cho server URLs.
3. Mutation provider gọi API trước hoặc optimistic có rollback; mapping content/text, fullName/name, avatarUrl/avatar, reactionCount/likes, createdAt/time, parentCommentId/parentId, uppercase enums. Không dùng post.comments.length thay commentCount khi mới load một page.
4. Chat dùng STOMP native endpoint có sẵn; dedup sender echo, đánh dấu read REST, poll khi disconnected và giữ draft. Notification dùng server IDs/read và REST fallback. UI action hủy/xóa phải khớp quyền backend nêu trên.
5. Explore tái dùng group posts + comments; stories dùng server list/view/reaction/chat. Không thêm service hoặc đổi stack để giải quyết khác biệt tên field.

## Nguồn mã đã đối chiếu

- `common/common-lib/src/main/java/com/socialapp/common/{dto,enums}`; `infra/api-gateway/src/main/{java/com/socialapp/gateway/filter/JwtAuthenticationFilter.java,resources/application.yml}`.
- Controllers/dtos/entities/documents và service liên quan trong `services/{user,post,feed,comment,reaction,moderation,chat,notification,group,search,story,reels,media}-service`.
- `frontend/lib/features/{profile,groups,chat}/..._repository.dart`, `frontend/lib/core/api/media_repository.dart`, `frontend/lib/core/ws/stomp_service.dart`.
- `frontend-web/AGENTS.md`, `frontend-web/src/lib/{types.ts,social-storage.ts}`, `frontend-web/src/components/{social-provider.tsx,screens.tsx,viewers.tsx}`.

Handoff phải giữ nguyên yêu cầu cộng tác của người dùng: tiếng Việt, i-have-adhd cho trình bày, ponytail full cho quyết định code; tích cực thực tế, chủ động tiến lên và phân biệt điều đã đọc từ code với kết quả đã chạy, không biến giới hạn thành rào cản hay tự tăng thí nghiệm/phạm vi.
