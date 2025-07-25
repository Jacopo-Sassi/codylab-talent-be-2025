package it.intesys.codylab.service;

import it.intesys.codylab.api.model.UsersApiDTO;
import it.intesys.codylab.mapper.UserMapper;
import it.intesys.codylab.model.User;
import it.intesys.codylab.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import java.util.ArrayList;

@Service
@Transactional
public class UserService {
    Pageable pageable = PageRequest.of(0, 10, Sort.by("id"));

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    public UserService(UserRepository userRepository, UserMapper userMapper) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
    }

    public Page<UsersApiDTO> getUsers() {
        return (Page<UsersApiDTO>) StreamSupport.stream(userRepository.findAll().spliterator(), false)
                .map(userMapper::toApiDTO)
                .collect(Collectors.toList());
    }

    public UsersApiDTO getUserById(Long id) {
        return userRepository.findUserWithoutProjects(id)
                .map(user -> {
                    user.setProjectManagers(null);
                    return userMapper.toApiDTO(user);
                })
                .orElseThrow(() -> new NoSuchElementException("User not found with id: " + id));
    }

    @Transactional
    public UsersApiDTO createUser(UsersApiDTO userDto) {
        User newUser = new User();
        newUser = userMapper.updateUserFromDto(userDto, newUser);
        newUser.setProjects(new ArrayList<>());
        newUser.setTasks(new ArrayList<>());
        newUser.setProjectManagers(new ArrayList<>());

        User savedUser = userRepository.save(newUser);
        return userMapper.toApiDTO(savedUser);
    }

    @Transactional
    public UsersApiDTO updateUser(Long id, UsersApiDTO userDto) {
        User existingUser = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + id));
        existingUser = userMapper.updateUserFromDto(userDto, existingUser);

        User savedUser = userRepository.save(existingUser);

        return userMapper.toApiDTO(savedUser);
    }

    public void deleteUser(Long id) {
        User user = userRepository.findById(id).orElse(null);
        assert user != null;
        user.getTasks().clear();
        user.getManagedProjects().clear();
        user.getProjects().clear();
        userRepository.deleteById(id);
    }

    public User findUserWithProjectManagers(Long id) {
        return userRepository.findUserWithManagedProjects(id);
    }

    public Page<UsersApiDTO> getUsers(
            List<Long> ids,
            Long taskId,
            Pageable pageable) {

        List<User> users = userRepository.findAll();

        if (ids != null && !ids.isEmpty()) {
            users = users.stream()
                    .filter(user -> ids.contains(user.getId()))
                    .toList();
        }

        if (taskId != null) {
            users = users.stream()
                    .filter(user -> taskId.equals(user.getTaskId()))
                    .toList();
        }

        List<UsersApiDTO> dtoList = users.stream()
                .map(user -> {
                    user.setProjectManagers(null);
                    return userMapper.toApiDTO(user);
                })
                .toList();

        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), dtoList.size());

        List<UsersApiDTO> pagedList = start <= end ? dtoList.subList(start, end) : List.of();

        return new org.springframework.data.domain.PageImpl<>(pagedList, pageable, dtoList.size());
    }
}
