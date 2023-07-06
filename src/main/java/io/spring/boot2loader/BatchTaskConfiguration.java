/*
 * Copyright 2021-2022 the original author or authors.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package io.spring.boot2loader;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameter;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.configuration.annotation.BatchConfigurer;
import org.springframework.batch.core.configuration.annotation.DefaultBatchConfigurer;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.repository.support.JobRepositoryFactoryBean;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.support.ListItemReader;
import org.springframework.batch.item.support.ListItemWriter;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.task.batch.listener.TaskBatchDao;
import org.springframework.cloud.task.batch.listener.support.JdbcTaskBatchDao;
import org.springframework.cloud.task.repository.TaskExecution;
import org.springframework.cloud.task.repository.TaskRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.*;

@Configuration
@EnableConfigurationProperties({ Boot2LoadProperties.class })
public class BatchTaskConfiguration {

	@Autowired
	public JobBuilderFactory jobBuilderFactory;

	@Autowired
	private JobLauncher jobLauncher;

	@Autowired
	private TaskRepository taskRepository;

	@Autowired
	private Boot2LoadProperties boot2LoadProperties;


	/**
	 * Override default transaction isolation level 'ISOLATION_REPEATABLE_READ' which Oracle does not
	 * support.
	 */
	@Configuration
	@ConditionalOnProperty(value = "spring.datasource.driver", havingValue = "oracle.jdbc.OracleDriver")
	static class OracleBatchConfig {
		@Bean
		BatchConfigurer oracleBatchConfigurer(DataSource dataSource) {
			return new DefaultBatchConfigurer() {
				@Override
				public JobRepository getJobRepository() {
					JobRepositoryFactoryBean factoryBean = new JobRepositoryFactoryBean();
					factoryBean.setDatabaseType("ORACLE");
					factoryBean.setDataSource(dataSource);
					factoryBean.setTransactionManager(getTransactionManager());
					factoryBean.setIsolationLevelForCreate("ISOLATION_READ_COMMITTED");
					try {
						return factoryBean.getObject();
					}
					catch (Exception e) {
						throw new BeanCreationException(e.getMessage(), e);
					}
				}
				@Override
				public DataSourceTransactionManager getTransactionManager() {
					return new DataSourceTransactionManager(dataSource);
				}
			};
		}
	}

	@Bean
	public CommandLineRunner commandLineRunner(DataSource dataSource, JobRepository jobRepository, PlatformTransactionManager transactionManager) {
		return new CommandLineRunner() {
			Job job = jobBuilderFactory.get("LoadGenerated Batch 4.0")
					.start(new StepBuilder("job1step1").repository(jobRepository).transactionManager(transactionManager)
							.<String, String>chunk(5)
							.reader(new ListItemReader<>(Collections.singletonList("hi")))
							.writer(new ListItemWriter<>())

							.build()).incrementer(new RunIdIncrementer())
							.build();
			@Override
			public void run(String... args) throws Exception {
				List<String> commandLineArgs = new ArrayList<>();
				commandLineArgs.add("-–spring.cloud.task.tablePrefix=TASK_");
				commandLineArgs.add("-–spring.batch.jdbc.table-prefix=BATCH_");

				for (int x = 0; x < boot2LoadProperties.jobsToCreate ; x++) {
					String uuid = UUID.randomUUID().toString();

					JobExecution jobExecution  = jobLauncher.run(job, new JobParameters(Collections.singletonMap("test", new JobParameter(uuid))));
					TaskExecution taskExecution = taskRepository.createTaskExecution("Demo Batch Job Task 4.0");
					taskRepository.startTaskExecution(taskExecution.getExecutionId(), "Demo Batch Job Task 4.0", new Date(),
							commandLineArgs, null);
					TaskBatchDao taskBatchDao = new JdbcTaskBatchDao(dataSource);
					taskBatchDao.saveRelationship(taskExecution, jobExecution);
					taskRepository.completeTaskExecution(taskExecution.getExecutionId(), 0, new Date(),
							"COMPLETE");

				}
			}
		};
	}

}

