package repositories;

import entities.GroupBO;
import entities.UserBO;
import messages.AuthMessage;
import messages.ChatMessage;
import messages.GroupMessage;
import messages.StatusMessage;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import javax.websocket.Session;
import java.util.List;

/**
 * @author Alexander Burghuber
 */
public class Repository {

    private static Repository ourInstance = new Repository();
    private EntityManager em;

    private Repository() {
        EntityManagerFactory emf = Persistence.createEntityManagerFactory("ChatPU");
        em = emf.createEntityManager();
    }

    public static Repository getInstance() {
        return ourInstance;
    }

    public void register(Session session, AuthMessage message) {
        String username = message.getUsername();
        String password = message.getPassword();

        StatusMessage status = new StatusMessage("status", "register");

        long count = em.createNamedQuery("User.count-username", Long.class)
                .setParameter("username", username)
                .getSingleResult();

        if (count == 0) {
            em.getTransaction().begin();
            em.persist(new UserBO(session, username, password));
            em.getTransaction().commit();
            status.setSuccess(true);
            status.setContent("Success");
        } else {
            status.setSuccess(false);
            status.setContent("Username is already taken");
        }
        session.getAsyncRemote().sendObject(status);
    }

    public void login(Session session, AuthMessage message) {
        String username = message.getUsername();
        String password = message.getPassword();

        StatusMessage status = new StatusMessage("status", "login");

        List<UserBO> users = em.createNamedQuery("User.get-with-username", UserBO.class)
                .setParameter("username", username)
                .getResultList();

        if (users.size() == 1) {
            UserBO user = users.get(0);
            if (user.getPassword().equals(password)) {
                session.getUserProperties().put("username", username);
                user.setSession(session);
                status.setSuccess(true);
                status.setContent("Success");
            } else {
                status.setSuccess(false);
                status.setContent("Username or password is wrong");
            }
        } else {
            status.setSuccess(false);
            status.setContent("Username or password is wrong");
        }
        session.getAsyncRemote().sendObject(status);
    }

    public void sendChat(Session session, ChatMessage chatMessage) {
        String target = chatMessage.getTarget();
        if (target.equals("user")) {
            List<UserBO> users = em.createNamedQuery("User.get-with-username", UserBO.class)
                    .setParameter("username", chatMessage.getName())
                    .getResultList();

            if (users.size() == 1) {
                UserBO userOfTarget = users.get(0);
                Session sessionOfTarget = userOfTarget.getSession();
                if (sessionOfTarget != null && sessionOfTarget.isOpen()) {
                    String usernameOfSender = (String) session.getUserProperties().get("username");
                    sessionOfTarget.getAsyncRemote().sendText("From " + usernameOfSender + ": " + chatMessage.getContent());
                } else {
                    // TODO: Response target is not online
                }
            } else {
                // TODO: Response user does not exist
            }
        } else if (target.equals("group")) {
            List<GroupBO> groups = em.createNamedQuery("Group.get-with-name", GroupBO.class)
                    .setParameter("name", chatMessage.getName())
                    .getResultList();

            if (groups.size() == 1) {
                GroupBO group = groups.get(0);
                for (UserBO member : group.getMembers()) {
                    Session memberSession = member.getSession();
                    if (memberSession != null && memberSession.isOpen()) {
                        memberSession.getAsyncRemote().sendText(chatMessage.getContent());
                    }
                }
            } else {
                // TODO: Response group doesn't exist
            }
        }
    }

    public void manageGroup(Session session, GroupMessage message) {
        String action = message.getAction();

        UserBO user = em.createNamedQuery("User.get-with-username", UserBO.class)
                .setParameter("username", session.getUserProperties().get("username"))
                .getSingleResult();
        if (action.equals("create")) {
            long count = em.createNamedQuery("Group.count-name", Long.class)
                    .setParameter("name", message.getName())
                    .getSingleResult();
            if (count == 0) {
                GroupBO group = new GroupBO(message.getName());
                em.getTransaction().begin();
                group.getMembers().add(user);
                em.persist(group);
                em.getTransaction().commit();
            } else {
                // TODO: Response group already exists
            }
        } else if (action.equals("join")) {
            List<GroupBO> groups = em.createNamedQuery("Group.get-with-name", GroupBO.class)
                    .setParameter("name", message.getName())
                    .getResultList();

            if (groups.size() == 1) {
                GroupBO group = groups.get(0);
                em.getTransaction().begin();
                group.getMembers().add(user);
                em.getTransaction().commit();
            } else {
                // TODO: Response group doesn't exist
            }
        }
    }

}
