package com.socialapp.fanpage.repository;

import com.socialapp.fanpage.entity.Fanpage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FanpageRepository extends JpaRepository<Fanpage, String> {

    Page<Fanpage> findByNameContainingIgnoreCase(String name, Pageable pageable);
}
