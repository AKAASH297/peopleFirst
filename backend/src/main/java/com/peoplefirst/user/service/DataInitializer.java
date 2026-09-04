package com.peoplefirst.user.service;

import com.peoplefirst.leave.service.LeaveBalanceService;
import com.peoplefirst.user.entity.Role;
import com.peoplefirst.user.entity.User;
import com.peoplefirst.user.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Component
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final LeaveBalanceService leaveBalanceService;

    public DataInitializer(UserRepository userRepository,
                           PasswordEncoder passwordEncoder,
                           LeaveBalanceService leaveBalanceService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.leaveBalanceService = leaveBalanceService;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (userRepository.count() > 0) {
            return;
        }

        String encodedPassword = passwordEncoder.encode("password123");
        int currentYear = LocalDate.now().getYear();

        // 1. Admin 1
        User admin1 = new User(
                "admin1",
                "admin1@peoplefirst.internal",
                encodedPassword,
                "Aditi Sharma (Admin)",
                Role.ADMIN,
                false,
                "Executive",
                "Bangalore",
                null
        );
        admin1 = userRepository.save(admin1);
        leaveBalanceService.initializeUserBalancesIfAbsent(admin1, currentYear);

        // 2. Admin 2
        User admin2 = new User(
                "admin2",
                "admin2@peoplefirst.internal",
                encodedPassword,
                "Arun Patel (Admin)",
                Role.ADMIN,
                false,
                "Executive",
                "Bangalore",
                null
        );
        admin2 = userRepository.save(admin2);
        leaveBalanceService.initializeUserBalancesIfAbsent(admin2, currentYear);

        // 3. Manager 1 (Engineering - reports to Admin 1)
        User manager1 = new User(
                "manager1",
                "manager1@peoplefirst.internal",
                encodedPassword,
                "Vikram Malhotra (Eng Manager)",
                Role.MANAGER,
                false,
                "Engineering",
                "Bangalore",
                admin1.getId()
        );
        manager1 = userRepository.save(manager1);
        leaveBalanceService.initializeUserBalancesIfAbsent(manager1, currentYear);

        // 4. Employee 1 (Engineering - reports to Manager 1)
        User employee1 = new User(
                "employee1",
                "employee1@peoplefirst.internal",
                encodedPassword,
                "Rohan Verma (Software Engineer)",
                Role.EMPLOYEE,
                false,
                "Engineering",
                "Bangalore",
                manager1.getId()
        );
        employee1 = userRepository.save(employee1);
        leaveBalanceService.initializeUserBalancesIfAbsent(employee1, currentYear);

        // 5. Contractor 1 (Engineering - reports to Manager 1)
        User contractor1 = new User(
                "contractor1",
                "contractor1@peoplefirst.internal",
                encodedPassword,
                "Kavita Nair (Contract Specialist)",
                Role.CONTRACTOR,
                true, // isContractor = true
                "Engineering",
                "Bangalore",
                manager1.getId()
        );
        contractor1 = userRepository.save(contractor1);
        leaveBalanceService.initializeUserBalancesIfAbsent(contractor1, currentYear);

        // 6. Manager 2 (Product - reports to Admin 1)
        User manager2 = new User(
                "manager2",
                "manager2@peoplefirst.internal",
                encodedPassword,
                "Priya Sen (Product Manager)",
                Role.MANAGER,
                false,
                "Product",
                "Hyderabad",
                admin1.getId()
        );
        manager2 = userRepository.save(manager2);
        leaveBalanceService.initializeUserBalancesIfAbsent(manager2, currentYear);

        // 7. Employee 2 (Product - reports to Manager 2)
        User employee2 = new User(
                "employee2",
                "employee2@peoplefirst.internal",
                encodedPassword,
                "Ananya Gupta (Product Designer)",
                Role.EMPLOYEE,
                false,
                "Product",
                "Hyderabad",
                manager2.getId()
        );
        employee2 = userRepository.save(employee2);
        leaveBalanceService.initializeUserBalancesIfAbsent(employee2, currentYear);
    }
}
